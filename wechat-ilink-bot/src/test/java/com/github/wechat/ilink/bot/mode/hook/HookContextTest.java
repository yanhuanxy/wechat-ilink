package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.mode.BotModeType;
import com.github.wechat.ilink.bot.mode.ModeOutcome;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookContextTest {

    @Test
    void builder_setsAllFields() {
        PlayerSession session = new PlayerSession("user1");
        ModeOutcome outcome = ModeOutcome.handled();
        Throwable t = new RuntimeException("x");

        HookContext ctx = HookContext.builder()
                .userId("user1")
                .text("hi")
                .session(session)
                .targetMode(BotModeType.CHAT)
                .fromMode(BotModeType.CHAT)
                .toMode(BotModeType.CLAUDE)
                .outcome(outcome)
                .throwable(t)
                .durationMs(123L)
                .sendKind("text")
                .build();

        assertEquals("user1", ctx.getUserId());
        assertEquals("hi", ctx.getText());
        assertSame(session, ctx.getSession());
        assertEquals(BotModeType.CHAT, ctx.getTargetMode());
        assertEquals(BotModeType.CHAT, ctx.getFromMode());
        assertEquals(BotModeType.CLAUDE, ctx.getToMode());
        assertSame(outcome, ctx.getOutcome());
        assertSame(t, ctx.getThrowable());
        assertEquals(123L, ctx.getDurationMs());
        assertEquals("text", ctx.getSendKind());
    }

    @Test
    void builder_empty_defaultsNullAndZero() {
        HookContext ctx = HookContext.builder().build();

        assertNull(ctx.getUserId());
        assertNull(ctx.getText());
        assertNull(ctx.getSession());
        assertEquals(0L, ctx.getDurationMs());
    }
}
