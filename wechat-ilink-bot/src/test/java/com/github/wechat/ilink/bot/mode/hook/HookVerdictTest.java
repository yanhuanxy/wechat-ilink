package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.mode.ModeOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookVerdictTest {

    @Test
    void continue_returnsContinueDecision() {
        HookVerdict v = HookVerdict.continue_();

        assertEquals(HookVerdict.Decision.CONTINUE, v.getDecision());
        assertTrue(v.isContinue());
        assertFalse(v.isBlock());
        assertFalse(v.isShortCircuit());
        assertNull(v.getReason());
        assertNull(v.getOutcome());
    }

    @Test
    void block_returnsBlockDecisionWithReason() {
        HookVerdict v = HookVerdict.block("forbidden");

        assertTrue(v.isBlock());
        assertEquals("forbidden", v.getReason());
        assertNull(v.getOutcome());
    }

    @Test
    void shortCircuit_returnsShortCircuitDecisionWithOutcome() {
        ModeOutcome outcome = ModeOutcome.handled();

        HookVerdict v = HookVerdict.shortCircuit(outcome);

        assertTrue(v.isShortCircuit());
        assertSame(outcome, v.getOutcome());
        assertNull(v.getReason());
    }

    @Test
    void block_nullReason_allowed() {
        HookVerdict v = HookVerdict.block(null);

        assertTrue(v.isBlock());
        assertNull(v.getReason());
    }
}
