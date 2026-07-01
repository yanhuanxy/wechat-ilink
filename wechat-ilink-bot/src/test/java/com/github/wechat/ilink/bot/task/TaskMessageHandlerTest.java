package com.github.wechat.ilink.bot.task;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VideoItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskMessageHandlerTest {

    private ILinkClient client;
    private TaskProvider taskProvider;
    private VideoTaskBuffer videoBuffer;
    private ExecutorService executor;
    private TaskMessageHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(ILinkClient.class);
        taskProvider = mock(TaskProvider.class);
        videoBuffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        executor = Executors.newSingleThreadExecutor();
        handler = new TaskMessageHandler(taskProvider, videoBuffer, executor);
        handler.setClient(client);
    }

    @Test
    void tryHandleVideo_messageWithVideo_downloadsAndBuffers() throws Exception {
        MessageItem item = new MessageItem();
        item.setVideo_item(new VideoItem());
        WeixinMessage msg = msgWithItems(item);
        when(client.downloadVideoFromMessageItem(item)).thenReturn(new byte[]{1, 2, 3});

        boolean handled = handler.tryHandleVideo("user1", msg);

        assertTrue(handled);
        verify(client).downloadVideoFromMessageItem(item);
        verify(client).sendText(eq("user1"), contains("已收到视频"));
        assertEquals(1, videoBuffer.size());
    }

    @Test
    void tryHandleVideo_messageWithoutVideo_returnsFalse() throws Exception {
        WeixinMessage msg = msgWithItems(new MessageItem());

        boolean handled = handler.tryHandleVideo("user1", msg);

        assertFalse(handled);
        verifyNoInteractions(client);
    }

    @Test
    void tryHandleVideo_downloadFails_sendsError() throws Exception {
        MessageItem item = new MessageItem();
        item.setVideo_item(new VideoItem());
        WeixinMessage msg = msgWithItems(item);
        when(client.downloadVideoFromMessageItem(item)).thenThrow(new RuntimeException("network"));

        boolean handled = handler.tryHandleVideo("user1", msg);

        assertTrue(handled);
        verify(client).sendText(eq("user1"), contains("视频下载失败"));
    }

    @Test
    void tryHandleTaskText_noBufferedVideo_returnsFalse() throws Exception {
        boolean handled = handler.tryHandleTaskText("user1", "分析视频");

        assertFalse(handled);
        verifyNoInteractions(taskProvider);
    }

    @Test
    void tryHandleTaskText_withBufferedVideo_acceptsAndSubmits() throws Exception {
        videoBuffer.put("user1", new byte[]{1, 2, 3}, "input.mp4");
        final AtomicReference<TaskRequest> captured = new AtomicReference<TaskRequest>(null);
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(taskProvider).execute(any(), any());

        boolean handled = handler.tryHandleTaskText("user1", "分析视频");

        assertTrue(handled);
        verify(client).sendText(eq("user1"), contains("任务已提交"));
        verify(taskProvider, timeout(1000)).execute(any(), any());
        assertNotNull(captured.get());
        assertEquals("user1", captured.get().getUserId());
        assertArrayEquals(new byte[]{1, 2, 3}, captured.get().getVideoBytes());
    }

    @Test
    void tryHandleTaskText_multiplePromptsWithinWindow_allHit() throws Exception {
        // peek（非消费）使 60s 窗口内多条 prompt 都命中同一视频票据（对齐 mode-router.md 的"后续 prompt"语义）
        videoBuffer.put("user1", new byte[]{1, 2, 3}, "input.mp4");
        doNothing().when(taskProvider).execute(any(), any());

        boolean first = handler.tryHandleTaskText("user1", "点评节奏");
        boolean second = handler.tryHandleTaskText("user1", "再说指法");

        assertTrue(first, "窗口内第 1 条 prompt 应命中评测");
        assertTrue(second, "窗口内第 2 条 prompt 也应命中评测（peek 不消费票据）");
        verify(client, times(2)).sendText(eq("user1"), contains("任务已提交"));
        verify(taskProvider, timeout(1000).times(2)).execute(any(), any());
        assertTrue(videoBuffer.hasPending("user1"), "peek 后票据仍在，靠 TTL 回收");
    }

    @Test
    void tryHandleTaskText_userTextContainsIntermediate_propagatesRubricId() throws Exception {
        videoBuffer.put("user1", new byte[]{1, 2, 3}, "input.mp4");
        final AtomicReference<TaskRequest> captured = new AtomicReference<TaskRequest>(null);
        doAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return null;
        }).when(taskProvider).execute(any(), any());

        handler.tryHandleTaskText("user1", "按中级标准点评");

        verify(taskProvider, timeout(1000)).execute(any(), any());
        assertNotNull(captured.get());
        assertEquals("intermediate", captured.get().getRubricId(),
                "用户文字中的中级关键词应解析为 intermediate rubricId");
    }

    @Test
    void parseRubricId_explicitBeginnerKeyword_returnsBeginner() {
        assertEquals("beginner", TaskMessageHandler.parseRubricId("按初级标准点评"));
        assertEquals("beginner", TaskMessageHandler.parseRubricId("启蒙阶段"));
        assertEquals("beginner", TaskMessageHandler.parseRubricId("beginner please"));
    }

    @Test
    void parseRubricId_intermediateKeyword_returnsIntermediate() {
        assertEquals("intermediate", TaskMessageHandler.parseRubricId("用进阶标准"));
        assertEquals("intermediate", TaskMessageHandler.parseRubricId("按中级来"));
    }

    @Test
    void parseRubricId_examKeyword_returnsExamPrep() {
        assertEquals("exam-prep", TaskMessageHandler.parseRubricId("考级冲刺"));
        assertEquals("exam-prep", TaskMessageHandler.parseRubricId("这是考试曲目"));
        assertEquals("exam-prep", TaskMessageHandler.parseRubricId("exam prep"));
    }

    @Test
    void parseRubricId_noKeyword_returnsNull() {
        assertNull(TaskMessageHandler.parseRubricId("看看弹得怎么样"));
        assertNull(TaskMessageHandler.parseRubricId("随便点评一下"));
    }

    @Test
    void parseRubricId_nullOrEmpty_returnsNull() {
        assertNull(TaskMessageHandler.parseRubricId(null));
        assertNull(TaskMessageHandler.parseRubricId(""));
    }

    @Test
    void splitMessage_shortText_returnsOneChunk() throws Exception {
        List<String> chunks = TaskMessageHandler.splitMessage("hello", 2000);

        assertEquals(1, chunks.size());
        assertEquals("hello", chunks.get(0));
    }

    @Test
    void splitMessage_longText_splitByLimit() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("x");
        String text = sb.toString();

        List<String> chunks = TaskMessageHandler.splitMessage(text, 30);

        assertTrue(chunks.size() >= 3);
        for (String c : chunks) {
            assertTrue(c.length() <= 30);
        }
    }

    @Test
    void splitMessage_emptyText_returnsEmptyList() throws Exception {
        List<String> chunks = TaskMessageHandler.splitMessage("", 2000);
        assertTrue(chunks.isEmpty());

        List<String> chunks2 = TaskMessageHandler.splitMessage(null, 2000);
        assertTrue(chunks2.isEmpty());
    }

    private WeixinMessage msgWithItems(MessageItem... items) {
        WeixinMessage msg = new WeixinMessage();
        List<MessageItem> list = new ArrayList<MessageItem>();
        Collections.addAll(list, items);
        msg.setItem_list(list);
        return msg;
    }
}
