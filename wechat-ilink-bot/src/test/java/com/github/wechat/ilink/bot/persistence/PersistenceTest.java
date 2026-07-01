package com.github.wechat.ilink.bot.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() {
        String dbPath = new File(tempDir, "test.db").getAbsolutePath();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    void databaseManager_createsTables() {
        assertNotNull(dbManager.getConnection());
    }

    @Test
    void actionRank_incrementAndQuery() {
        ActionRankRepository repo = new ActionRankRepository(dbManager);
        repo.incrementScore("PEST", "user1", 10);
        repo.incrementScore("PEST", "user2", 5);
        repo.incrementScore("PEST", "user1", 3);

        java.util.Map<String, Integer> ranking = repo.getTopScores("PEST", 10);
        assertEquals(2, ranking.size());
    }
}
