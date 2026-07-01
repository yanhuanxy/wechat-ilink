package com.github.wechat.ilink.bot.mode.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookRegistryTest {

    @Test
    void has_noRegistration_returnsFalse() {
        HookRegistry registry = new HookRegistry();

        assertFalse(registry.has(HookEvent.PRE_SEND));
    }

    @Test
    void has_afterRegistration_returnsTrue() {
        HookRegistry registry = new HookRegistry();
        registry.register(new ContinueHook(HookEvent.PRE_SEND));

        assertTrue(registry.has(HookEvent.PRE_SEND));
    }

    @Test
    void register_null_ignored() {
        HookRegistry registry = new HookRegistry();

        registry.register(null);

        assertFalse(registry.has(HookEvent.PRE_SEND));
    }

    @Test
    void fire_emptyRegistry_returnsContinue() {
        HookRegistry registry = new HookRegistry();

        HookVerdict v = registry.fire(HookEvent.ON_ERROR, null);

        assertTrue(v.isContinue());
    }

    @Test
    void fire_allContinue_returnsContinue() {
        HookRegistry registry = new HookRegistry();
        registry.register(new ContinueHook(HookEvent.PRE_SEND));
        registry.register(new ContinueHook(HookEvent.PRE_SEND));

        HookVerdict v = registry.fire(HookEvent.PRE_SEND, HookContext.builder().build());

        assertTrue(v.isContinue());
    }

    @Test
    void fire_firstNonContinueStopsIteration() {
        HookRegistry registry = new HookRegistry();
        RecordingHook first = new RecordingHook(HookEvent.PRE_SEND, HookVerdict.continue_());
        RecordingHook blocking = new RecordingHook(HookEvent.PRE_SEND, HookVerdict.block("nope"));
        RecordingHook third = new RecordingHook(HookEvent.PRE_SEND, HookVerdict.continue_());
        registry.register(first);
        registry.register(blocking);
        registry.register(third);

        HookVerdict v = registry.fire(HookEvent.PRE_SEND, HookContext.builder().build());

        assertTrue(v.isBlock());
        assertEquals("nope", v.getReason());
        assertTrue(first.invoked);
        assertTrue(blocking.invoked);
        assertFalse(third.invoked, "阻断后不应继续触发后续 hook");
    }

    @Test
    void fire_inOrderOfRegistration() {
        HookRegistry registry = new HookRegistry();
        StringBuilder seq = new StringBuilder();
        registry.register(new AppendHook(HookEvent.PRE_SEND, "a", seq));
        registry.register(new AppendHook(HookEvent.PRE_SEND, "b", seq));

        registry.fire(HookEvent.PRE_SEND, HookContext.builder().build());

        assertEquals("ab", seq.toString());
    }

    @Test
    void fire_hookThrows_doesNotPropagate_andContinues() {
        HookRegistry registry = new HookRegistry();
        registry.register(new ThrowingHook(HookEvent.PRE_SEND));
        registry.register(new ContinueHook(HookEvent.PRE_SEND));

        HookVerdict v = registry.fire(HookEvent.PRE_SEND, HookContext.builder().build());

        assertTrue(v.isContinue(), "异常 hook 应被吞掉，后续 hook 仍执行");
    }

    /** 放行型测试 hook。 */
    static final class ContinueHook implements BotHook {
        private final HookEvent event;

        ContinueHook(HookEvent event) {
            this.event = event;
        }

        @Override
        public HookEvent event() {
            return event;
        }

        @Override
        public HookVerdict handle(HookContext ctx) {
            return HookVerdict.continue_();
        }
    }

    /** 记录是否被触发，并返回预设 verdict。 */
    static final class RecordingHook implements BotHook {
        private final HookEvent event;
        private final HookVerdict verdict;
        boolean invoked;

        RecordingHook(HookEvent event, HookVerdict verdict) {
            this.event = event;
            this.verdict = verdict;
        }

        @Override
        public HookEvent event() {
            return event;
        }

        @Override
        public HookVerdict handle(HookContext ctx) {
            invoked = true;
            return verdict;
        }
    }

    static final class ThrowingHook implements BotHook {
        private final HookEvent event;

        ThrowingHook(HookEvent event) {
            this.event = event;
        }

        @Override
        public HookEvent event() {
            return event;
        }

        @Override
        public HookVerdict handle(HookContext ctx) {
            throw new IllegalStateException("boom");
        }
    }

    static final class AppendHook implements BotHook {
        private final HookEvent event;
        private final String token;
        private final StringBuilder seq;

        AppendHook(HookEvent event, String token, StringBuilder seq) {
            this.event = event;
            this.token = token;
            this.seq = seq;
        }

        @Override
        public HookEvent event() {
            return event;
        }

        @Override
        public HookVerdict handle(HookContext ctx) {
            seq.append(token);
            return HookVerdict.continue_();
        }
    }
}
