package com.github.wechat.ilink.bot.persistence;

import com.github.wechat.ilink.bot.mode.claude.ClaudeSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeSessionRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private ClaudeSessionRepository repo;

    @BeforeEach
    void setUp() {
        String dbPath = new File(tempDir, "claude.db").getAbsolutePath();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
        repo = new ClaudeSessionRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void insertAndFindById_roundTrips() {
        ClaudeSession session = new ClaudeSession("sid-1", "user1", "data/cwd", "model-x", "标题", 100L, 200L);

        repo.insert(session);
        ClaudeSession found = repo.findById("sid-1");

        assertNotNull(found);
        assertEquals("sid-1", found.getSessionId());
        assertEquals("user1", found.getUserId());
        assertEquals("data/cwd", found.getCwd());
        assertEquals("model-x", found.getModel());
        assertEquals("标题", found.getTitle());
        assertEquals(100L, found.getCreatedAt());
        assertEquals(200L, found.getUpdatedAt());
    }

    @Test
    void findById_unknown_returnsNull() {
        assertNull(repo.findById("missing"));
    }

    @Test
    void findByUserIdOrderByUpdatedDesc_ordersByUpdatedDescending() {
        repo.insert(new ClaudeSession("old", "user1", null, null, "旧", 1L, 1000L));
        repo.insert(new ClaudeSession("new", "user1", null, null, "新", 1L, 3000L));
        repo.insert(new ClaudeSession("mid", "user1", null, null, "中", 1L, 2000L));
        repo.insert(new ClaudeSession("other", "user2", null, null, "他人", 1L, 9000L));

        List<ClaudeSession> list = repo.findByUserIdOrderByUpdatedDesc("user1", 10);

        assertEquals(3, list.size());
        assertEquals("new", list.get(0).getSessionId());
        assertEquals("mid", list.get(1).getSessionId());
        assertEquals("old", list.get(2).getSessionId());
    }

    @Test
    void findByUserIdOrderByUpdatedDesc_respectsLimit() {
        repo.insert(new ClaudeSession("a", "user1", null, null, "a", 1L, 1000L));
        repo.insert(new ClaudeSession("b", "user1", null, null, "b", 1L, 2000L));
        repo.insert(new ClaudeSession("c", "user1", null, null, "c", 1L, 3000L));

        List<ClaudeSession> list = repo.findByUserIdOrderByUpdatedDesc("user1", 2);

        assertEquals(2, list.size());
        assertEquals("c", list.get(0).getSessionId());
        assertEquals("b", list.get(1).getSessionId());
    }

    @Test
    void touchUpdatedAt_updatesTimestamp() {
        repo.insert(new ClaudeSession("sid", "user1", null, null, "t", 100L, 200L));

        repo.touchUpdatedAt("sid", 5000L);

        ClaudeSession found = repo.findById("sid");
        assertEquals(5000L, found.getUpdatedAt());
        assertEquals(100L, found.getCreatedAt());
    }

    @Test
    void insert_sameSessionId_replacesExisting() {
        repo.insert(new ClaudeSession("sid", "user1", null, null, "first", 1L, 1L));
        repo.insert(new ClaudeSession("sid", "user1", null, null, "second", 2L, 2L));

        assertEquals("second", repo.findById("sid").getTitle());
        assertEquals(1, repo.findByUserIdOrderByUpdatedDesc("user1", 10).size());
    }

    @Test
    void deleteByUserIdAndSessionId_removesRow() {
        repo.insert(new ClaudeSession("sid", "user1", null, null, "t", 1L, 1L));

        repo.deleteByUserIdAndSessionId("user1", "sid");

        assertNull(repo.findById("sid"));
    }

    @Test
    void deleteByUserIdAndSessionId_wrongUser_keepsRow() {
        repo.insert(new ClaudeSession("sid", "user1", null, null, "t", 1L, 1L));

        repo.deleteByUserIdAndSessionId("user2", "sid");

        assertNotNull(repo.findById("sid"));
    }
}
