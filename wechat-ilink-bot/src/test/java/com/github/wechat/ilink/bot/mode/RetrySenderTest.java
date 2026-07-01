package com.github.wechat.ilink.bot.mode;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetrySenderTest {

    @Test
    void sendText_succeedsFirstTry_delegatesOnce() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        RetrySender sender = new RetrySender(delegate, 3, 0L);

        sender.sendText("user1", "hi");

        verify(delegate).sendText("user1", "hi");
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void sendText_failsTwiceThenSucceeds_retriesAndCompletes() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        doThrow(new IOException("e1")).doThrow(new IOException("e2")).when(delegate).sendText(anyString(), anyString());
        RetrySender sender = new RetrySender(delegate, 3, 0L);

        sender.sendText("user1", "hi");

        verify(delegate, times(3)).sendText("user1", "hi");
    }

    @Test
    void sendText_allFail_givesUpWithoutThrowing() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        doThrow(new IOException("boom")).when(delegate).sendText(anyString(), anyString());
        RetrySender sender = new RetrySender(delegate, 3, 0L);

        // 不向上抛（尽力发送语义）
        sender.sendText("user1", "hi");

        verify(delegate, times(3)).sendText("user1", "hi");
    }

    @Test
    void sendImage_allFail_givesUpWithoutThrowing() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        doThrow(new IOException("boom")).when(delegate)
                .sendImage(anyString(), any(byte[].class), anyString(), anyString());
        RetrySender sender = new RetrySender(delegate, 2, 0L);

        sender.sendImage("user1", new byte[]{1}, "a.png", "");

        verify(delegate, times(2)).sendImage(eq("user1"), any(byte[].class), eq("a.png"), anyString());
    }

    @Test
    void maxAttemptsOne_noRetry() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        doThrow(new IOException("boom")).when(delegate).sendFile(anyString(), any(byte[].class), anyString(), anyString());
        RetrySender sender = new RetrySender(delegate, 1, 100L);

        sender.sendFile("user1", new byte[]{1}, "a.txt", "");

        verify(delegate, times(1)).sendFile(eq("user1"), any(byte[].class), eq("a.txt"), anyString());
    }

    @Test
    void startTyping_propagatesAndDoesNotRetry() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        doThrow(new IOException("boom")).when(delegate).startTyping(anyString());
        RetrySender sender = new RetrySender(delegate, 3, 0L);

        assertThrows(IOException.class, () -> sender.startTyping("user1"));

        verify(delegate, times(1)).startTyping("user1");
    }

    @Test
    void sendTextWithTyping_propagatesAndDoesNotRetry() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        doThrow(new IOException("boom")).when(delegate).sendTextWithTyping(anyString(), anyString(), anyLong());
        RetrySender sender = new RetrySender(delegate, 3, 0L);

        assertThrows(IOException.class, () -> sender.sendTextWithTyping("user1", "hi", 100L));

        verify(delegate, times(1)).sendTextWithTyping("user1", "hi", 100L);
    }

    @Test
    void stopTyping_delegatesDirectly() throws Exception {
        ModeSender delegate = mock(ModeSender.class);
        RetrySender sender = new RetrySender(delegate, 3, 0L);

        sender.stopTyping("user1");

        verify(delegate).stopTyping("user1");
    }
}
