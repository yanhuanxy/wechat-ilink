package com.github.wechat.ilink.bot.session;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class FlushGateTest {

    @Test
    void scheduleFlush_delayZero_callsFlushNowSynchronously() {
        SessionManager sm = mock(SessionManager.class);
        FlushGate gate = new FlushGate(sm, 0L, 0L);

        gate.scheduleFlush("user1");

        verify(sm).flushNow("user1");
    }

    @Test
    void scheduleFlush_positiveDelay_debouncesToOneFlush() {
        SessionManager sm = mock(SessionManager.class);
        FlushGate gate = new FlushGate(sm, 30L, 0L);
        gate.start();

        gate.scheduleFlush("user1");
        gate.scheduleFlush("user1");
        gate.scheduleFlush("user1");

        // 短窗口内 3 次请求合并为 1 次落盘
        verify(sm, timeout(500).times(1)).flushNow("user1");
        gate.shutdown();
    }

    @Test
    void flushAllNow_callsFlushAllDirty() {
        SessionManager sm = mock(SessionManager.class);
        FlushGate gate = new FlushGate(sm, 0L, 0L);

        gate.flushAllNow();

        verify(sm).flushAllDirty();
    }

    @Test
    void startAndShutdown_idempotent() {
        SessionManager sm = mock(SessionManager.class);
        FlushGate gate = new FlushGate(sm, 0L, 1000L);

        gate.start();
        gate.start();
        gate.shutdown();
        gate.shutdown();
    }
}
