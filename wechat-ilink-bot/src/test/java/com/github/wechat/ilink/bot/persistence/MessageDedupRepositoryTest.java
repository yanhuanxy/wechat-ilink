package com.github.wechat.ilink.bot.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息去重水位线仓库测试——重启 resume 重投幂等的关键路径。
 * 覆盖：无记录水位线、上移、不回退、取 max、跨实例持久化、per-user 隔离。
 */
class MessageDedupRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private MessageDedupRepository repo;

    @BeforeEach
    void setUp() {
        dbManager = new DatabaseManager(new File(tempDir, "dedup.db").getAbsolutePath());
        dbManager.initialize();
        repo = new MessageDedupRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void getLastMessageId_noRecord_returnsMinValue() {
        assertEquals(Long.MIN_VALUE, repo.getLastMessageId("u1"));
    }

    @Test
    void markProcessed_advancesWatermark() {
        repo.markProcessed("u1", 100L);
        assertEquals(100L, repo.getLastMessageId("u1"));
    }

    @Test
    void markProcessed_lowerId_doesNotRegress() {
        repo.markProcessed("u1", 100L);
        repo.markProcessed("u1", 50L); // resume 重投旧消息，水位线不回退
        assertEquals(100L, repo.getLastMessageId("u1"));
    }

    @Test
    void markProcessed_accumulatesMax() {
        repo.markProcessed("u1", 10L);
        repo.markProcessed("u1", 30L);
        repo.markProcessed("u1", 20L);
        assertEquals(30L, repo.getLastMessageId("u1"));
    }

    @Test
    void markProcessed_persistsAcrossInstances() {
        repo.markProcessed("u1", 200L);
        // 新实例模拟重启：缓存清空，须从 DB 加载
        MessageDedupRepository restarted = new MessageDedupRepository(dbManager);
        assertEquals(200L, restarted.getLastMessageId("u1"));
    }

    @Test
    void watermark_isolatedPerUser() {
        repo.markProcessed("u1", 100L);
        repo.markProcessed("u2", 500L);
        assertEquals(100L, repo.getLastMessageId("u1"));
        assertEquals(500L, repo.getLastMessageId("u2"));
    }
}
