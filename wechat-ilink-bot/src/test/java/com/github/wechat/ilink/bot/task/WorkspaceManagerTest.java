package com.github.wechat.ilink.bot.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceManagerTest {

    @TempDir
    File tempDir;

    @Test
    void prepareTaskDir_createsNestedDirectory() throws Exception {
        WorkspaceManager mgr = new WorkspaceManager(tempDir.getAbsolutePath());

        Path dir = mgr.prepareTaskDir("user1", "task-abc");

        assertTrue(Files.exists(dir));
        assertTrue(Files.isDirectory(dir));
        assertEquals("user1", dir.getParent().getFileName().toString());
        assertEquals("task-abc", dir.getFileName().toString());
    }

    @Test
    void prepareTaskDir_sanitizesUnsafeSegments() throws Exception {
        WorkspaceManager mgr = new WorkspaceManager(tempDir.getAbsolutePath());

        Path dir = mgr.prepareTaskDir("..\\evil\\user", "task/with/slash");

        assertTrue(Files.exists(dir));
        assertTrue(dir.toString().contains("evil"));
    }

    @Test
    void writeVideo_writesBytesToFile() throws Exception {
        WorkspaceManager mgr = new WorkspaceManager(tempDir.getAbsolutePath());
        Path dir = mgr.prepareTaskDir("user1", "task-1");
        byte[] video = {1, 2, 3, 4, 5};

        Path target = mgr.writeVideo(dir, video, "input.mp4");

        assertTrue(Files.exists(target));
        assertArrayEquals(video, Files.readAllBytes(target));
    }

    @Test
    void pickVideoFileName_invalidChars_replaced() {
        WorkspaceManager mgr = new WorkspaceManager(tempDir.getAbsolutePath());

        assertEquals("clip.mp4", mgr.pickVideoFileName("clip.mp4"));
        assertEquals("a_b_c.mp4", mgr.pickVideoFileName("a/b/c.mp4"));
        assertEquals("input.mp4", mgr.pickVideoFileName(null));
        assertEquals("input.mp4", mgr.pickVideoFileName(""));
        assertEquals("input.mp4", mgr.pickVideoFileName("///"));
    }

    @Test
    void sanitize_emptyOrUnsafe_returnsFallback() {
        WorkspaceManager mgr = new WorkspaceManager(tempDir.getAbsolutePath());

        assertEquals("unknown", mgr.sanitize(null));
        assertEquals("unknown", mgr.sanitize(""));
        assertEquals("a_b", mgr.sanitize("a/b"));
    }
}
