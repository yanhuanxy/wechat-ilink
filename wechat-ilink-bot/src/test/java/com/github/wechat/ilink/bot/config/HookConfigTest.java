package com.github.wechat.ilink.bot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class HookConfigTest {

    @TempDir
    File tempDir;

    @Test
    void defaults_allEnabled() {
        HookConfig config = new HookConfig();

        assertTrue(config.isAudit());
        assertTrue(config.isRateLimit());
    }

    @Test
    void load_missingFile_returnsDefaults() {
        File missing = new File(tempDir, "nope/hooks-config.json");

        HookConfig config = HookConfig.load(missing.getAbsolutePath());

        assertTrue(config.isAudit());
        assertTrue(config.isRateLimit());
        assertTrue(missing.exists(), "模板应已生成");
    }

    @Test
    void load_validFile_appliesValues() throws Exception {
        File file = new File(tempDir, "hooks-config.json");
        FileWriter w = new FileWriter(file);
        w.write("{\"audit\":false,\"rateLimit\":true}");
        w.close();

        HookConfig config = HookConfig.load(file.getAbsolutePath());

        assertFalse(config.isAudit());
        assertTrue(config.isRateLimit());
    }
}
