package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.engine.GameEngine;
import com.github.wechat.ilink.bot.engine.ResponseRenderer;
import com.github.wechat.ilink.bot.llm.ChatHistoryManager;
import com.github.wechat.ilink.bot.llm.ChatMessage;
import com.github.wechat.ilink.bot.llm.LlmProvider;
import com.github.wechat.ilink.bot.llm.LlmRequestQueue;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.session.SessionManager;
import com.github.wechat.ilink.bot.task.TaskMessageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatModeTest {

    private LlmProvider llmProvider;
    private ChatHistoryManager history;
    private LlmRequestQueue queue;
    private ModeSender sender;
    private SessionManager sessions;
    private PlayerSession session;
    private ModeContext streamingCtx;
    private ModeContext syncCtx;

    @BeforeEach
    void setUp() {
        llmProvider = mock(LlmProvider.class);
        history = new ChatHistoryManager(20);
        queue = new LlmRequestQueue(3, 50);
        sender = mock(ModeSender.class);
        sessions = mock(SessionManager.class);
        session = new PlayerSession("user1");

        streamingCtx = ModeContext.builder().sender(sender)
                .engine(mock(GameEngine.class)).renderer(mock(ResponseRenderer.class))
                .llmProvider(llmProvider).chatHistory(history).llmQueue(queue)
                .sessions(sessions).taskHandler(mock(TaskMessageHandler.class))
                .streamingEnabled(true).typingIntervalMs(5000).build();
        syncCtx = ModeContext.builder().sender(sender)
                .engine(mock(GameEngine.class)).renderer(mock(ResponseRenderer.class))
                .llmProvider(llmProvider).chatHistory(history).llmQueue(queue)
                .sessions(sessions).taskHandler(mock(TaskMessageHandler.class))
                .typingIntervalMs(5000).build();
    }

    @AfterEach
    void tearDown() {
        queue.shutdown();
    }

    @Test
    void type_returnsChat() {
        assertEquals(BotModeType.CHAT, new ChatMode().type());
    }

    @Test
    void handleText_nullProvider_echoesText() throws Exception {
        ModeContext noProvider = ModeContext.builder().sender(sender)
                .engine(mock(GameEngine.class)).renderer(mock(ResponseRenderer.class))
                .chatHistory(history).llmQueue(queue)
                .sessions(sessions).typingIntervalMs(5000).build();
        ChatMode mode = new ChatMode();

        ModeOutcome outcome = mode.handleText(noProvider, session, "你好");

        assertTrue(outcome.isHandled());
        verify(sender).sendText("user1", "你好");
    }

    @Test
    void handleText_syncMode_callsChatAndSendsReply() throws Exception {
        when(llmProvider.chat(anyList())).thenReturn("AI 回复");
        ChatMode mode = new ChatMode();

        ModeOutcome outcome = mode.handleText(syncCtx, session, "你好");

        assertTrue(outcome.isHandled());
        verify(llmProvider, timeout(1000)).chat(anyList());
        verify(sender, timeout(1000)).sendTextWithTyping(eq("user1"), eq("AI 回复"), eq(3000L));
    }

    @Test
    void handleText_syncMode_nullReply_sendsFallback() throws Exception {
        when(llmProvider.chat(anyList())).thenReturn(null);
        ChatMode mode = new ChatMode();

        mode.handleText(syncCtx, session, "你好");

        verify(sender, timeout(1000)).sendText(eq("user1"), contains("暂时无法回复"));
    }

    @Test
    void handleText_syncMode_chatReturns_isCompleted() throws Exception {
        when(llmProvider.chat(anyList())).thenReturn("AI 回复");
        ChatMode mode = new ChatMode();

        ModeOutcome outcome = mode.handleText(syncCtx, session, "你好");

        assertTrue(outcome.isHandled());
        verify(llmProvider, timeout(1000)).chat(anyList());
    }

    @Test
    void handleText_streamingMode_callsChatStream() throws Exception {
        ChatMode mode = new ChatMode();

        mode.handleText(streamingCtx, session, "你好");

        verify(llmProvider, timeout(1000)).chatStream(anyList(), any(StreamCallback.class));
        verify(sender, timeout(1000)).startTyping("user1");
    }

    @Test
    void handleText_streamingOnComplete_sendsFullResponse() throws Exception {
        final StreamCallback[] callbackHolder = new StreamCallback[1];
        doAnswer(inv -> {
            callbackHolder[0] = inv.getArgument(1);
            return null;
        }).when(llmProvider).chatStream(anyList(), any(StreamCallback.class));

        ChatMode mode = new ChatMode();
        mode.handleText(streamingCtx, session, "你好");

        verify(llmProvider, timeout(1000)).chatStream(anyList(), any(StreamCallback.class));
        assertNotNull(callbackHolder[0]);

        callbackHolder[0].onToken("Hello ");
        callbackHolder[0].onToken("World");
        callbackHolder[0].onComplete("Hello World");

        verify(sender, timeout(1000)).sendText("user1", "Hello World");
        verify(sender, timeout(1000)).stopTyping("user1");
    }

    @Test
    void handleText_streamingOnError_sendsPartialResponse() throws Exception {
        final StreamCallback[] callbackHolder = new StreamCallback[1];
        doAnswer(inv -> {
            callbackHolder[0] = inv.getArgument(1);
            return null;
        }).when(llmProvider).chatStream(anyList(), any(StreamCallback.class));

        ChatMode mode = new ChatMode();
        mode.handleText(streamingCtx, session, "你好");

        verify(llmProvider, timeout(1000)).chatStream(anyList(), any(StreamCallback.class));
        assertNotNull(callbackHolder[0]);
        callbackHolder[0].onToken("部分");
        callbackHolder[0].onError(new RuntimeException("upstream"));

        verify(sender, timeout(1000)).sendText("user1", "部分");
        verify(sender, timeout(1000)).stopTyping("user1");
    }

    @Test
    void handleText_streamingOnError_emptyPartial_sendsFallback() throws Exception {
        final StreamCallback[] callbackHolder = new StreamCallback[1];
        doAnswer(inv -> {
            callbackHolder[0] = inv.getArgument(1);
            return null;
        }).when(llmProvider).chatStream(anyList(), any(StreamCallback.class));

        ChatMode mode = new ChatMode();
        mode.handleText(streamingCtx, session, "你好");

        verify(llmProvider, timeout(1000)).chatStream(anyList(), any(StreamCallback.class));
        assertNotNull(callbackHolder[0]);
        callbackHolder[0].onError(new RuntimeException("upstream"));

        verify(sender, timeout(1000)).sendText(eq("user1"), contains("暂时无法回复"));
    }

    @Test
    void handleText_historyRecordsUserAndAssistant() throws Exception {
        when(llmProvider.chat(anyList())).thenReturn("AI 回复");
        ChatMode mode = new ChatMode();

        mode.handleText(syncCtx, session, "你好");

        verify(llmProvider, timeout(1000)).chat(anyList());
        verify(sender, timeout(1000)).sendTextWithTyping(eq("user1"), eq("AI 回复"), eq(3000L));
        List<ChatMessage> all = history.getHistory("user1");
        assertEquals(2, all.size());
        assertEquals("user", all.get(0).getRole());
        assertEquals("assistant", all.get(1).getRole());
    }

    @Test
    void buildSystemPrompt_containsGameState() {
        String prompt = ChatMode.buildSystemPrompt(session);
        assertTrue(prompt.contains("500"));
        assertTrue(prompt.contains("Lv.1"));
        assertTrue(prompt.contains("4/36"));
    }
}
