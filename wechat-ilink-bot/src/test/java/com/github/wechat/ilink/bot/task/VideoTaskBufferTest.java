package com.github.wechat.ilink.bot.task;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VideoTaskBufferTest {

    @Test
    void put_validPayload_accepted() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        byte[] video = {1, 2, 3};
        VideoTaskBuffer.PutResult result = buffer.put("user1", video, "input.mp4");

        assertTrue(result.isAccepted());
        assertNull(result.getErrorMessage());
        assertEquals(1, buffer.size());
    }

    @Test
    void put_emptyUserId_rejected() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        VideoTaskBuffer.PutResult result = buffer.put("", new byte[]{1}, "x.mp4");

        assertFalse(result.isAccepted());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void put_emptyBytes_rejected() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        VideoTaskBuffer.PutResult result = buffer.put("user1", new byte[0], "x.mp4");

        assertFalse(result.isAccepted());
    }

    @Test
    void put_oversized_rejected() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 100L);
        byte[] big = new byte[101];
        VideoTaskBuffer.PutResult result = buffer.put("user1", big, "big.mp4");

        assertFalse(result.isAccepted());
        assertTrue(result.getErrorMessage().contains("视频过大"));
    }

    @Test
    void consume_afterPut_returnsTicket() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        byte[] video = {1, 2, 3};
        buffer.put("user1", video, "clip.mp4");

        VideoTaskBuffer.VideoTicket ticket = buffer.consume("user1");

        assertNotNull(ticket);
        assertArrayEquals(video, ticket.getVideoBytes());
        assertEquals("clip.mp4", ticket.getFileName());
        assertEquals(0, buffer.size());
    }

    @Test
    void consume_withoutPut_returnsNull() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);

        VideoTaskBuffer.VideoTicket ticket = buffer.consume("user1");

        assertNull(ticket);
    }

    @Test
    void consume_twice_secondReturnsNull() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        buffer.put("user1", new byte[]{1}, "x.mp4");

        assertNotNull(buffer.consume("user1"));
        assertNull(buffer.consume("user1"));
    }

    @Test
    void hasPending_afterPut_trueOtherFalse() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        buffer.put("user1", new byte[]{1}, "x.mp4");

        assertTrue(buffer.hasPending("user1"));
        assertFalse(buffer.hasPending("other"), "未缓冲的用户应为 false");
        assertFalse(buffer.hasPending(null), "null userId 应为 false");
    }

    @Test
    void peek_afterPut_returnsTicketWithoutRemoving() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        byte[] video = {1, 2, 3};
        buffer.put("user1", video, "clip.mp4");

        VideoTaskBuffer.VideoTicket first = buffer.peek("user1");
        VideoTaskBuffer.VideoTicket second = buffer.peek("user1");

        assertNotNull(first);
        assertArrayEquals(video, first.getVideoBytes());
        assertEquals("clip.mp4", first.getFileName());
        assertNotNull(second, "peek 不移除票据，第二次仍可取");
        assertEquals(1, buffer.size(), "peek 不改变 size");
    }

    @Test
    void peek_withoutPut_returnsNull() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);

        assertNull(buffer.peek("user1"));
        assertNull(buffer.peek(null));
    }

    @Test
    void purgeExpired_removesStaleTickets() throws InterruptedException {
        VideoTaskBuffer buffer = new VideoTaskBuffer(50L, 1024 * 1024L);
        buffer.put("user1", new byte[]{1}, "x.mp4");
        Thread.sleep(80);

        buffer.purgeExpired();

        assertEquals(0, buffer.size());
    }

    @Test
    void purgeExpired_keepsFreshTickets() {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        buffer.put("user1", new byte[]{1}, "x.mp4");

        buffer.purgeExpired();

        assertEquals(1, buffer.size());
    }

    @Test
    void concurrent_put_sameUserId_lastWins() throws InterruptedException {
        VideoTaskBuffer buffer = new VideoTaskBuffer(60_000L, 1024 * 1024L);
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch latch = new CountDownLatch(threads);
        final AtomicInteger accepted = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (buffer.put("user1", new byte[]{(byte) idx}, "x.mp4").isAccepted()) {
                            accepted.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        latch.await();
        pool.shutdown();

        assertEquals(threads, accepted.get());
        assertEquals(1, buffer.size());
    }
}
