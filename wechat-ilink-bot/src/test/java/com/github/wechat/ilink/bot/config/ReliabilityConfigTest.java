package com.github.wechat.ilink.bot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class ReliabilityConfigTest {

    @TempDir
    File tempDir;

    @Test
    void defaults_allSet() {
        ReliabilityConfig config = new ReliabilityConfig();

        assertEquals(3, config.getSendMaxAttempts());
        assertEquals(500L, config.getSendBackoffMs());
        assertEquals(30, config.getRateLimitPerMin());
        assertEquals(60_000L, config.getRateLimitWindowMs());
        assertEquals(0L, config.getFlushDelayMs(), "默认同步立即刷");
        assertEquals(30_000L, config.getFlushIntervalMs());
        assertEquals(30_000L, config.getMcpHealthIntervalMs());
        assertEquals(2, config.getMcpToolRefreshTicks());
    }

    @Test
    void load_missingFile_returnsDefaults() {
        File missing = new File(tempDir, "nope/reliability-config.json");

        ReliabilityConfig config = ReliabilityConfig.load(missing.getAbsolutePath());

        assertEquals(3, config.getSendMaxAttempts());
        assertEquals(0L, config.getFlushDelayMs());
        // 模板应已生成
        assertTrue(missing.exists());
    }

    @Test
    void load_validFile_appliesValues() throws Exception {
        File file = new File(tempDir, "reliability-config.json");
        FileWriter w = new FileWriter(file);
        w.write("{\"sendMaxAttempts\":5,\"sendBackoffMs\":250,\"rateLimitPerMin\":10,"
                + "\"rateLimitWindowMs\":30000,\"flushDelayMs\":800,\"flushIntervalMs\":10000,"
                + "\"mcpHealthIntervalMs\":15000,\"mcpToolRefreshTicks\":4}");
        w.close();

        ReliabilityConfig config = ReliabilityConfig.load(file.getAbsolutePath());

        assertEquals(5, config.getSendMaxAttempts());
        assertEquals(250L, config.getSendBackoffMs());
        assertEquals(10, config.getRateLimitPerMin());
        assertEquals(800L, config.getFlushDelayMs(), "应启用 debounce 合并");
        assertEquals(15_000L, config.getMcpHealthIntervalMs());
        assertEquals(4, config.getMcpToolRefreshTicks());
    }
}
