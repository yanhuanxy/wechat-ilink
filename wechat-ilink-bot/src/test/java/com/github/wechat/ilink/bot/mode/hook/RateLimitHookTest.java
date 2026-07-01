package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.mode.ModeSender;
import com.github.wechat.ilink.bot.mode.RateLimiter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitHookTest {

    @Test
    void event_isOnMessageReceived() {
        assertEquals(HookEvent.ON_MESSAGE_RECEIVED,
                new RateLimitHook(new RateLimiter(1, 1000L), null).event());
    }

    @Test
    void handle_underLimit_continues() {
        RateLimiter limiter = new RateLimiter(5, 60_000L);
        RateLimitHook hook = new RateLimitHook(limiter, mock(ModeSender.class));

        HookVerdict v = hook.handle(HookContext.builder().userId("user1").build());

        assertTrue(v.isContinue());
    }

    @Test
    void handle_overLimit_shortCircuitsAndSendsBusy() throws Exception {
        RateLimiter limiter = new RateLimiter(1, 60_000L);
        ModeSender sender = mock(ModeSender.class);
        RateLimitHook hook = new RateLimitHook(limiter, sender);
        HookContext ctx = HookContext.builder().userId("user1").build();

        hook.handle(ctx);
        HookVerdict blocked = hook.handle(ctx);

        assertTrue(blocked.isShortCircuit());
        assertNotNull(blocked.getOutcome());
        assertTrue(blocked.getOutcome().isHandled());
        verify(sender).sendText(eq("user1"), contains("请求过于频繁"));
    }

    @Test
    void handle_nullLimiter_continues() {
        RateLimitHook hook = new RateLimitHook(null, mock(ModeSender.class));

        HookVerdict v = hook.handle(HookContext.builder().userId("user1").build());

        assertTrue(v.isContinue());
    }

    @Test
    void handle_nullUserId_continues() {
        RateLimitHook hook = new RateLimitHook(new RateLimiter(1, 1000L), mock(ModeSender.class));

        HookVerdict v = hook.handle(HookContext.builder().build());

        assertTrue(v.isContinue());
    }

    @Test
    void handle_sendThrowsIOException_shortCircuitsAnyway() throws Exception {
        RateLimiter limiter = new RateLimiter(1, 60_000L);
        ModeSender sender = mock(ModeSender.class);
        doThrow(new IOException("down")).when(sender).sendText(anyString(), anyString());
        RateLimitHook hook = new RateLimitHook(limiter, sender);
        HookContext ctx = HookContext.builder().userId("user1").build();

        hook.handle(ctx);
        HookVerdict blocked = hook.handle(ctx);

        assertTrue(blocked.isShortCircuit(), "发送失败应被吞掉，仍 short-circuit 路由");
    }
}
