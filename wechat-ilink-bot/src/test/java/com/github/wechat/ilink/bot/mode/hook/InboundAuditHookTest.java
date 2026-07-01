package com.github.wechat.ilink.bot.mode.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InboundAuditHookTest {

    @Test
    void event_isOnTextReceived() {
        assertEquals(HookEvent.ON_TEXT_RECEIVED, new InboundAuditHook().event());
    }

    @Test
    void handle_writesAuditAndContinues() {
        InboundAuditHook hook = new InboundAuditHook();
        HookContext ctx = HookContext.builder().userId("user1").text("你好").build();

        HookVerdict verdict = assertDoesNotThrow(() -> hook.handle(ctx));

        assertTrue(verdict.isContinue());
    }

    @Test
    void handle_nullFields_doesNotThrow() {
        InboundAuditHook hook = new InboundAuditHook();
        HookContext ctx = HookContext.builder().build();

        assertDoesNotThrow(() -> hook.handle(ctx));
    }
}
