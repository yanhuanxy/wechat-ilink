package com.github.wechat.ilink.bot.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 劳动类榜单仓库测试——补 PersistenceTest 只断言 size 的缺口。
 * 覆盖：同用户累加、降序保序、limit 截断、按 actionType 隔离、空榜单。
 */
class ActionRankRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private ActionRankRepository repo;

    @BeforeEach
    void setUp() {
        dbManager = new DatabaseManager(new File(tempDir, "rank.db").getAbsolutePath());
        dbManager.initialize();
        repo = new ActionRankRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void incrementScore_accumulatesForSameUser() {
        repo.incrementScore("PEST", "u1", 10);
        repo.incrementScore("PEST", "u1", 3);
        Map<String, Integer> ranking = repo.getTopScores("PEST", 10);
        assertEquals(13, ranking.get("u1"));
    }

    @Test
    void getTopScores_ordersByScoreDesc() {
        repo.incrementScore("PEST", "low", 5);
        repo.incrementScore("PEST", "high", 50);
        repo.incrementScore("PEST", "mid", 20);
        Iterator<String> order = repo.getTopScores("PEST", 10).keySet().iterator();
        assertEquals("high", order.next());
        assertEquals("mid", order.next());
        assertEquals("low", order.next());
    }

    @Test
    void getTopScores_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            repo.incrementScore("PEST", "u" + i, i);
        }
        assertEquals(3, repo.getTopScores("PEST", 3).size());
    }

    @Test
    void getTopScores_isolatedPerActionType() {
        repo.incrementScore("PEST", "u1", 10);
        repo.incrementScore("WATER", "u1", 20);
        assertEquals(10, repo.getTopScores("PEST", 10).get("u1"));
        assertEquals(20, repo.getTopScores("WATER", 10).get("u1"));
    }

    @Test
    void getTopScores_empty_returnsEmptyMap() {
        assertTrue(repo.getTopScores("NONE", 10).isEmpty());
    }
}
