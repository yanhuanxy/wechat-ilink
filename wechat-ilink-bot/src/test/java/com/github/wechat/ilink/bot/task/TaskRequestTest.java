package com.github.wechat.ilink.bot.task;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskRequestTest {

    @Test
    void taskId_validUuidFormat() {
        TaskRequest req = new TaskRequest("user-abc", new byte[]{1}, "x.mp4", "hi", null);

        assertDoesNotThrow(() -> UUID.fromString(req.getTaskId()));
    }

    @Test
    void taskId_uniquePerRequest() {
        TaskRequest a = new TaskRequest("u", new byte[]{1}, "x", "y", null);
        TaskRequest b = new TaskRequest("u", new byte[]{1}, "x", "y", null);

        assertNotEquals(a.getTaskId(), b.getTaskId());
    }

    @Test
    void fields_preservedFromConstructor() {
        TaskRequest req = new TaskRequest("user-xyz", new byte[]{1, 2, 3}, "clip.mp4", "看看弹的", "intermediate");

        assertEquals("user-xyz", req.getUserId());
        assertArrayEquals(new byte[]{1, 2, 3}, req.getVideoBytes());
        assertEquals("clip.mp4", req.getVideoFileName());
        assertEquals("看看弹的", req.getUserPrompt());
        assertEquals("intermediate", req.getRubricId());
    }
}
