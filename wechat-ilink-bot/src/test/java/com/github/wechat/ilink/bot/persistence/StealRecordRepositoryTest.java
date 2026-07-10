package com.github.wechat.ilink.bot.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 偷菜记录仓库测试——跨玩家无锁设计的落账基础。
 * 覆盖全部 7 个查询：record / sumStolen / hasStolen / sumStolenByVictim /
 * sumCompensation / sumCompensationByVictim / clearPlot，含 PK 唯一性与跨周期边界。
 */
class StealRecordRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private StealRecordRepository repo;

    @BeforeEach
    void setUp() {
        dbManager = new DatabaseManager(new File(tempDir, "steal.db").getAbsolutePath());
        dbManager.initialize();
        repo = new StealRecordRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void record_insertsAndSumsStolen() {
        assertTrue(repo.record("v1", 0, "0", "t1", 3, 9));
        assertEquals(3, repo.sumStolen("v1", 0, "0"));
    }

    @Test
    void record_duplicateThiefSamePlotCycle_returnsFalse() {
        assertTrue(repo.record("v1", 0, "0", "t1", 3, 9));
        assertFalse(repo.record("v1", 0, "0", "t1", 2, 6)); // 同贼同地同周期不可重复偷
    }

    @Test
    void record_differentThiefSamePlot_bothSucceedAndAccumulate() {
        assertTrue(repo.record("v1", 0, "0", "t1", 3, 9));
        assertTrue(repo.record("v1", 0, "0", "t2", 2, 6)); // 不同贼可各偷一次
        assertEquals(5, repo.sumStolen("v1", 0, "0")); // 被偷量累加
    }

    @Test
    void record_newCycleSamePlot_sameThiefCanStealAgain() {
        repo.record("v1", 0, "0", "t1", 3, 9);
        assertTrue(repo.record("v1", 0, "100", "t1", 2, 6)); // 新成熟周期，同贼可再偷
        assertEquals(3, repo.sumStolen("v1", 0, "0"));
        assertEquals(2, repo.sumStolen("v1", 0, "100"));
    }

    @Test
    void hasStolen_reflectsRecord() {
        assertFalse(repo.hasStolen("v1", 0, "0", "t1"));
        repo.record("v1", 0, "0", "t1", 3, 9);
        assertTrue(repo.hasStolen("v1", 0, "0", "t1"));
        assertFalse(repo.hasStolen("v1", 0, "0", "t2")); // 另一贼未偷
    }

    @Test
    void sumStolenByVictim_aggregatesAcrossPlots() {
        repo.record("v1", 0, "0", "t1", 3, 9);
        repo.record("v1", 1, "0", "t2", 2, 6);
        assertEquals(5, repo.sumStolenByVictim("v1"));
    }

    @Test
    void clearPlot_removesAllCyclesForPlot() {
        repo.record("v1", 0, "0", "t1", 3, 9);
        repo.record("v1", 0, "100", "t2", 2, 6);
        repo.clearPlot("v1", 0);
        assertEquals(0, repo.sumStolen("v1", 0, "0"));
        assertEquals(0, repo.sumStolen("v1", 0, "100"));
    }

    @Test
    void compensation_queriesMatchStolenScope() {
        repo.record("v1", 0, "0", "t1", 3, 9);
        repo.record("v1", 1, "0", "t2", 2, 6);
        assertEquals(9, repo.sumCompensation("v1", 0, "0"));
        assertEquals(15, repo.sumCompensationByVictim("v1"));
    }

    @Test
    void queries_noRecord_returnZero() {
        assertEquals(0, repo.sumStolen("nobody", 0, "0"));
        assertEquals(0, repo.sumStolenByVictim("nobody"));
        assertEquals(0, repo.sumCompensationByVictim("nobody"));
    }
}
