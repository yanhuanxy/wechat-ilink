package com.github.wechat.ilink.bot.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void tryAcquire_withinLimit_returnsTrue() {
        RateLimiter limiter = new RateLimiter(3, 60_000L);

        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
    }

    @Test
    void tryAcquire_overLimit_returnsFalse() {
        RateLimiter limiter = new RateLimiter(2, 60_000L);

        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));
    }

    @Test
    void tryAcquire_windowResets_allowsAgain() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(2, 50L);

        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user1"));
        assertFalse(limiter.tryAcquire("user1"));

        Thread.sleep(70L);

        assertTrue(limiter.tryAcquire("user1"), "窗口滚动后应重新放行");
    }

    @Test
    void tryAcquire_nullUserId_returnsTrue() {
        RateLimiter limiter = new RateLimiter(1, 60_000L);

        assertTrue(limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire(""));
    }

    @Test
    void tryAcquire_independentUsers() {
        RateLimiter limiter = new RateLimiter(1, 60_000L);

        assertTrue(limiter.tryAcquire("user1"));
        assertTrue(limiter.tryAcquire("user2"), "不同用户独立计数");
        assertFalse(limiter.tryAcquire("user1"));
    }

    @Test
    void purgeExpired_removesOldWindows() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(5, 50L);
        limiter.tryAcquire("user1");
        limiter.tryAcquire("user2");
        assertEquals(2, limiter.size());

        Thread.sleep(70L);
        limiter.purgeExpired();

        assertEquals(0, limiter.size());
    }

    @Test
    void startCleanupAndShutdown_noThrow() {
        RateLimiter limiter = new RateLimiter(5, 60_000L);

        limiter.startCleanup();
        limiter.startCleanup(); // 幂等
        limiter.shutdown();
        limiter.shutdown(); // 幂等
    }
}
