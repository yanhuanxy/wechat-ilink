package com.github.wechat.ilink.bot.mode.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboundAuditHookTest {

    @Test
    void event_isPreSend() {
        assertEquals(HookEvent.PRE_SEND, new OutboundAuditHook().event());
    }

    @Test
    void handle_writesAuditAndContinues() {
        OutboundAuditHook hook = new OutboundAuditHook();
        HookContext ctx = HookContext.builder().userId("user1").sendKind("text").text("回复").build();

        HookVerdict verdict = assertDoesNotThrow(() -> hook.handle(ctx));

        assertTrue(verdict.isContinue());
    }

    @Test
    void handle_nullFields_doesNotThrow() {
        OutboundAuditHook hook = new OutboundAuditHook();

        assertDoesNotThrow(() -> hook.handle(HookContext.builder().build()));
    }
}
