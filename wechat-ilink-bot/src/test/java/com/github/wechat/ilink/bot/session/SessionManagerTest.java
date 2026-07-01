package com.github.wechat.ilink.bot.session;

import com.github.wechat.ilink.bot.farm.model.CropStage;
import com.github.wechat.ilink.bot.persistence.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        String dbPath = new File(tempDir, "test.db").getAbsolutePath();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
        sessionManager = new SessionManager(dbManager);
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    void getOrCreate_newUser_createsSession() {
        PlayerSession session = sessionManager.getOrCreate("newUser");
        assertNotNull(session);
        assertEquals("newUser", session.getUserId());
        assertEquals(500, session.getGold());
        assertEquals(1, session.getLevel());
    }

    @Test
    void getOrCreate_existingUser_returnsCached() {
        PlayerSession s1 = sessionManager.getOrCreate("user1");
        s1.setGold(999);
        PlayerSession s2 = sessionManager.getOrCreate("user1");
        assertEquals(999, s2.getGold());
        assertSame(s1, s2);
    }

    @Test
    void saveSession_persistsToDb() {
        PlayerSession session = sessionManager.getOrCreate("user1");
        session.setGold(1000);
        sessionManager.saveSession(session);

        sessionManager.remove("user1");
        PlayerSession loaded = sessionManager.getOrCreate("user1");
        assertEquals(1000, loaded.getGold());
    }

    @Test
    void flushAll_savesAllSessions() {
        PlayerSession s1 = sessionManager.getOrCreate("user1");
        s1.setGold(100);
        PlayerSession s2 = sessionManager.getOrCreate("user2");
        s2.setGold(200);

        sessionManager.flushAll();

        sessionManager.remove("user1");
        sessionManager.remove("user2");

        assertEquals(100, sessionManager.getOrCreate("user1").getGold());
        assertEquals(200, sessionManager.getOrCreate("user2").getGold());
    }

    @Test
    void saveSession_withPlots_persistsPlots() {
        PlayerSession session = sessionManager.getOrCreate("farmer1");
        session.getPlots().get(0).plant("wheat");
        sessionManager.saveSession(session);

        sessionManager.remove("farmer1");
        PlayerSession loaded = sessionManager.getOrCreate("farmer1");
        assertEquals("wheat", loaded.getPlots().get(0).getCropType());
        assertEquals(CropStage.SEED, loaded.getPlots().get(0).getStage());
    }

    @Test
    void saveSession_withInventory_persistsInventory() {
        PlayerSession session = sessionManager.getOrCreate("farmer1");
        session.getInventory().addSeed("wheat", 10);
        session.getInventory().addProduce("corn", 5);
        sessionManager.saveSession(session);

        sessionManager.remove("farmer1");
        PlayerSession loaded = sessionManager.getOrCreate("farmer1");
        assertEquals(10, loaded.getInventory().getSeedCount("wheat"));
        assertEquals(5, loaded.getInventory().getProduceCount("corn"));
    }

    @Test
    void saveSession_notDirty_skipsWrite() {
        PlayerSession session = sessionManager.getOrCreate("user1");
        session.clearDirty();
        sessionManager.saveSession(session);
    }

    @Test
    void lockFor_sameUser_returnsSameLock() {
        assertSame(sessionManager.lockFor("user1"), sessionManager.lockFor("user1"));
        assertNotSame(sessionManager.lockFor("user1"), sessionManager.lockFor("user2"));
    }

    @Test
    void withLock_runsTask() {
        final boolean[] ran = {false};
        sessionManager.withLock("user1", new Runnable() {
            @Override
            public void run() {
                ran[0] = true;
            }
        });
        assertTrue(ran[0]);
    }

    @Test
    void scheduleFlush_simpleConstructor_persistsSynchronously() {
        PlayerSession session = sessionManager.getOrCreate("user1");
        session.setGold(777);

        sessionManager.scheduleFlush("user1");

        sessionManager.remove("user1");
        PlayerSession loaded = sessionManager.getOrCreate("user1");
        assertEquals(777, loaded.getGold());
    }
}
