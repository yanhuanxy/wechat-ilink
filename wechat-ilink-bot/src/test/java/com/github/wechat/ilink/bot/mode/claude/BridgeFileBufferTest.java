package com.github.wechat.ilink.bot.mode.claude;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeFileBufferTest {

    @Test
    void put_validBytes_acceptedAndConsumable() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);
        byte[] bytes = "hello".getBytes();

        BridgeFileBuffer.PutResult result = buffer.put("user1", bytes, "a.txt", false);

        assertTrue(result.isAccepted());
        BridgeFileBuffer.FileTicket ticket = buffer.consume("user1");
        assertNotNull(ticket);
        assertArrayEquals(bytes, ticket.getBytes());
        assertEquals("a.txt", ticket.getFileName());
        assertFalse(ticket.isImage());
    }

    @Test
    void put_imageFlag_preservedOnConsume() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);

        buffer.put("user1", new byte[]{1, 2}, "pic.jpg", true);

        assertTrue(buffer.consume("user1").isImage());
    }

    @Test
    void put_emptyBytes_rejected() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);

        BridgeFileBuffer.PutResult result = buffer.put("user1", new byte[0], "a.txt", false);

        assertFalse(result.isAccepted());
        assertNotNull(result.getErrorMessage());
        assertEquals(0, buffer.size());
    }

    @Test
    void put_nullBytes_rejected() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);

        BridgeFileBuffer.PutResult result = buffer.put("user1", null, "a.txt", false);

        assertFalse(result.isAccepted());
        assertEquals(0, buffer.size());
    }

    @Test
    void put_oversize_rejected() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 4L);

        BridgeFileBuffer.PutResult result = buffer.put("user1", new byte[]{1, 2, 3, 4, 5}, "big.bin", false);

        assertFalse(result.isAccepted());
        assertTrue(result.getErrorMessage().contains("过大"));
    }

    @Test
    void put_nullUserId_rejected() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);

        assertFalse(buffer.put(null, new byte[]{1}, "a", false).isAccepted());
        assertFalse(buffer.put("", new byte[]{1}, "a", false).isAccepted());
    }

    @Test
    void consume_afterConsume_returnsNull() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);
        buffer.put("user1", new byte[]{1}, "a", false);

        assertNotNull(buffer.consume("user1"));
        assertNull(buffer.consume("user1"));
        assertEquals(0, buffer.size());
    }

    @Test
    void purgeExpired_removesExpiredTickets() throws InterruptedException {
        BridgeFileBuffer buffer = new BridgeFileBuffer(50L, 1024L);
        buffer.put("user1", new byte[]{1}, "a", false);
        assertEquals(1, buffer.size());

        Thread.sleep(120L);
        buffer.purgeExpired();

        assertEquals(0, buffer.size());
        assertNull(buffer.consume("user1"));
    }

    @Test
    void shutdown_clearsPending() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);
        buffer.put("user1", new byte[]{1}, "a", false);

        buffer.shutdown();

        assertEquals(0, buffer.size());
    }

    @Test
    void startCleanup_idempotentAndShutdownStopsIt() {
        BridgeFileBuffer buffer = new BridgeFileBuffer(60_000L, 1024L);

        buffer.startCleanup();
        buffer.startCleanup(); // 第二次应无副作用
        buffer.shutdown();      // 不抛异常，幂等
        buffer.shutdown();
    }
}
