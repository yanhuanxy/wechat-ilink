package com.github.wechat.ilink.bot.task;

import com.github.wechat.ilink.bot.llm.StreamCallback;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VideoItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TaskMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TaskMessageHandler.class);
    private static final int WECHAT_TEXT_LIMIT = 2000;

    private volatile ILinkClient client;
    private final TaskProvider taskProvider;
    private final VideoTaskBuffer videoBuffer;
    private final ExecutorService taskExecutor;

    public TaskMessageHandler(TaskProvider taskProvider, VideoTaskBuffer videoBuffer,
                              ExecutorService taskExecutor) {
        this.taskProvider = taskProvider;
        this.videoBuffer = videoBuffer;
        this.taskExecutor = taskExecutor;
    }

    public void setClient(ILinkClient client) {
        this.client = client;
    }

    public boolean tryHandleVideo(String userId, WeixinMessage msg) {
        MessageItem item = findVideoItem(msg);
        if (item == null) {
            log.debug("tryHandleVideo 未发现 video_item, userId={}", userId);
            return false;
        }
        handleIncomingVideo(userId, item);
        return true;
    }

    public boolean tryHandleTaskText(String userId, String text) {
        // peek（非消费）：60s 窗口内允许多次 prompt 命中同一视频票据，票据靠 TTL 回收（对齐 mode-router.md 的"后续 prompt"语义）。
        VideoTaskBuffer.VideoTicket ticket = videoBuffer.peek(userId);
        if (ticket == null) {
            log.info("评测 prompt 未命中票据, userId={}, hasPending={}", userId, videoBuffer.hasPending(userId));
            return false;
        }
        log.info("评测 prompt 命中票据, userId={}", userId);
        handleTask(userId, ticket, text);
        return true;
    }

    private void handleIncomingVideo(String userId, MessageItem item) {
        try {
            byte[] videoBytes = client.downloadVideoFromMessageItem(item);
            String fileName = "input_" + System.currentTimeMillis() + ".mp4";
            VideoTaskBuffer.PutResult result = videoBuffer.put(userId, videoBytes, fileName);
            if (result.isAccepted()) {
                log.info("视频票据已入缓冲, userId={}, hasPending={}", userId, videoBuffer.hasPending(userId));
                sendSafeText(userId, "🎬 已收到视频，请在 60 秒内发送处理说明（无需任何前缀）");
            } else {
                sendSafeText(userId, "视频处理失败：" + result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("下载视频失败, userId={}", userId, e);
            sendSafeText(userId, "视频下载失败，请重试");
        }
    }

    private void handleTask(String userId, VideoTaskBuffer.VideoTicket ticket, String userPrompt) {
        sendSafeText(userId, "🤖 任务已提交，AI 正在处理，请耐心等待...");
        String rubricId = parseRubricId(userPrompt);
        final TaskRequest req = new TaskRequest(userId, ticket.getVideoBytes(),
                ticket.getFileName(), userPrompt, rubricId);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                runTask(userId, req);
            }
        };
        try {
            taskExecutor.submit(task);
        } catch (Exception e) {
            log.error("任务提交失败, userId={}", userId, e);
            sendSafeText(userId, "任务队列已满，请稍后重试");
        }
    }

    private void runTask(final String userId, final TaskRequest req) {
        StreamCallback callback = new StreamCallback() {
            @Override
            public void onToken(String token) {
                // 不推送中间 token，等完成时一次性发
            }

            @Override
            public void onComplete(String fullResponse) {
                if (fullResponse == null || fullResponse.isEmpty()) {
                    sendSafeText(userId, "任务完成，但 AI 未返回内容");
                    return;
                }
                for (String chunk : splitMessage(fullResponse, WECHAT_TEXT_LIMIT)) {
                    sendSafeText(userId, chunk);
                }
            }

            @Override
            public void onError(Throwable t) {
                sendSafeText(userId, "任务执行失败：" + t.getMessage());
            }
        };
        try {
            taskProvider.execute(req, callback);
        } catch (Exception e) {
            log.error("任务执行异常, userId={}", userId, e);
            sendSafeText(userId, "任务执行异常：" + e.getMessage());
        }
    }

    private void sendSafeText(String userId, String text) {
        try {
            client.sendText(userId, text);
        } catch (Exception e) {
            log.error("发送消息失败, userId={}", userId, e);
        }
    }

    private MessageItem findVideoItem(WeixinMessage msg) {
        if (msg.getItem_list() == null) {
            return null;
        }
        for (MessageItem item : msg.getItem_list()) {
            VideoItem v = item.getVideo_item();
            if (v != null) {
                return item;
            }
        }
        return null;
    }

    public static List<String> splitMessage(String text, int maxLen) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            out.add(text.substring(start, end));
            start = end;
        }
        return out;
    }

    static String parseRubricId(String text) {
        if (text == null || text.isEmpty()) return null;
        String s = text.toLowerCase();
        if (containsAny(s, "考级", "考试", "模拟考", "exam")) return "exam-prep";
        if (containsAny(s, "中级", "进阶", "intermediate")) return "intermediate";
        if (containsAny(s, "初级", "启蒙", "入门", "beginner")) return "beginner";
        return null;
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }
}
