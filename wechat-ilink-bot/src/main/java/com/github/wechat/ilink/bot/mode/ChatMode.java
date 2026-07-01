package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.llm.ChatHistoryManager;
import com.github.wechat.ilink.bot.llm.ChatMessage;
import com.github.wechat.ilink.bot.llm.LlmProvider;
import com.github.wechat.ilink.bot.llm.LlmRequestQueue;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ChatMode implements BotMode {

    private static final Logger log = LoggerFactory.getLogger(ChatMode.class);

    @Override
    public BotModeType type() {
        return BotModeType.CHAT;
    }

    @Override
    public ModeOutcome handleText(ModeContext ctx, PlayerSession session, String text) {
        String userId = session.getUserId();
        LlmProvider provider = ctx.llmProvider();
        ModeSender sender = ctx.sender();

        if (provider == null) {
            try {
                sender.sendText(userId, text);
            } catch (Exception e) {
                log.error("回显消息失败, userId={}", userId, e);
            }
            return ModeOutcome.handled();
        }

        try {
            String systemPrompt = buildSystemPrompt(session);
            ChatHistoryManager history = ctx.chatHistory();
            history.addMessage(userId, "user", text);

            List<ChatMessage> allMessages = new ArrayList<ChatMessage>();
            allMessages.add(ChatMessage.system(systemPrompt));
            allMessages.addAll(history.getHistory(userId));

            if (ctx.streamingEnabled()) {
                handleStreamingChat(ctx, userId, allMessages);
            } else {
                handleSyncChat(ctx, userId, allMessages);
            }
        } catch (Exception e) {
            log.error("LLM 处理出错, userId={}", userId, e);
            sendSafeText(sender, userId, "AI 暂时无法回复，请稍后重试");
        }
        return ModeOutcome.handled();
    }

    private void handleStreamingChat(final ModeContext ctx, final String userId,
                                     final List<ChatMessage> messages) {
        final ModeSender sender = ctx.sender();
        try {
            sender.startTyping(userId);
        } catch (Exception e) {
            log.warn("启动 typing 指示器失败, userId={}", userId, e);
        }

        final StringBuilder responseBuilder = new StringBuilder();
        final long[] lastTypingTime = new long[]{System.currentTimeMillis()};
        final int typingIntervalMs = ctx.typingIntervalMs();
        final ChatHistoryManager history = ctx.chatHistory();
        final LlmProvider provider = ctx.llmProvider();

        StreamCallback callback = new StreamCallback() {
            @Override
            public void onToken(String token) {
                responseBuilder.append(token);
                long now = System.currentTimeMillis();
                if (now - lastTypingTime[0] > typingIntervalMs) {
                    lastTypingTime[0] = now;
                    try {
                        sender.startTyping(userId);
                    } catch (Exception e) {
                        log.warn("刷新 typing 指示器失败, userId={}", userId, e);
                    }
                }
            }

            @Override
            public void onComplete(String fullResponse) {
                try {
                    history.addMessage(userId, "assistant", fullResponse);
                    sender.sendText(userId, fullResponse);
                } catch (Exception e) {
                    log.error("发送流式回复失败, userId={}", userId, e);
                } finally {
                    stopTypingSafe(sender, userId);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("LLM 流式调用失败, userId={}", userId, t);
                String partial = responseBuilder.toString();
                if (!partial.isEmpty()) {
                    try {
                        history.addMessage(userId, "assistant", partial);
                        sender.sendText(userId, partial);
                    } catch (Exception e) {
                        log.error("发送部分回复失败, userId={}", userId, e);
                    }
                } else {
                    sendSafeText(sender, userId, "AI 暂时无法回复，请稍后重试");
                }
                stopTypingSafe(sender, userId);
            }
        };

        final LlmRequestQueue queue = ctx.llmQueue();
        final LlmProvider llmProvider = provider;
        boolean accepted = queue.submit(userId, new Runnable() {
            @Override
            public void run() {
                llmProvider.chatStream(messages, callback);
            }
        });
        if (!accepted) {
            stopTypingSafe(sender, userId);
            sendSafeText(sender, userId, "AI 当前繁忙，请稍后重试");
        }
    }

    private void handleSyncChat(final ModeContext ctx, final String userId,
                                final List<ChatMessage> messages) {
        final ModeSender sender = ctx.sender();
        final LlmProvider provider = ctx.llmProvider();
        final ChatHistoryManager history = ctx.chatHistory();
        final LlmRequestQueue queue = ctx.llmQueue();

        boolean accepted = queue.submit(userId, new Runnable() {
            @Override
            public void run() {
                String reply = provider.chat(messages);
                if (reply != null) {
                    history.addMessage(userId, "assistant", reply);
                    try {
                        sender.sendTextWithTyping(userId, reply, 3000L);
                    } catch (Exception e) {
                        log.error("发送回复失败, userId={}", userId, e);
                    }
                } else {
                    sendSafeText(sender, userId, "AI 暂时无法回复，请稍后重试");
                }
            }
        });
        if (!accepted) {
            sendSafeText(sender, userId, "AI 当前繁忙，请稍后重试");
        }
    }

    public static String buildSystemPrompt(PlayerSession session) {
        int threshold = session.getLevel() * 100;
        StringBuilder sb = new StringBuilder();
        sb.append("你是\"帮帮农场\"的游戏助手。当前玩家状态：\n");
        sb.append("金币: ").append(session.getGold());
        sb.append("  等级: Lv.").append(session.getLevel());
        sb.append(" (").append(session.getExp()).append("/").append(threshold).append(" EXP)\n");
        sb.append("已解锁地块: ").append(session.getMaxPlots()).append("/36\n");
        sb.append("背包: ").append(session.getInventory().summary()).append("\n\n");
        sb.append("你可以回答关于游戏的问题，也可以和玩家闲聊。\n");
        sb.append("如果玩家想执行游戏操作，提醒他们使用 # 前缀的指令，输入 #帮助 查看所有命令。");
        return sb.toString();
    }

    private static void stopTypingSafe(ModeSender sender, String userId) {
        try {
            sender.stopTyping(userId);
        } catch (Exception e) {
            log.warn("停止 typing 指示器失败, userId={}", userId, e);
        }
    }

    private static void sendSafeText(ModeSender sender, String userId, String text) {
        try {
            sender.sendText(userId, text);
        } catch (Exception e) {
            log.error("发送消息失败, userId={}", userId, e);
        }
    }
}
