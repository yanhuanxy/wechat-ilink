package com.github.wechat.ilink.bot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class AutogameConfigTest {

    @TempDir
    File tempDir;

    @Test
    void defaults_disabledWithLocalhostUrl() {
        AutogameConfig config = new AutogameConfig();
        assertFalse(config.isEnabled());
        assertEquals("http://localhost:8765", config.getMcpUrl());
    }

    @Test
    void load_missingFile_returnsDefaultsAndCreatesTemplate() {
        File missing = new File(tempDir, "sub/autogame-config.json");
        AutogameConfig config = AutogameConfig.load(missing.getAbsolutePath());
        assertFalse(config.isEnabled());
        assertEquals("http://localhost:8765", config.getMcpUrl());
        assertTrue(missing.exists()); // 模板已生成
    }

    @Test
    void load_validFile_appliesValues() throws Exception {
        File file = new File(tempDir, "autogame-config.json");
        write(file, "{\"enabled\":true,\"mcpUrl\":\"http://remote:9000\"}");
        AutogameConfig config = AutogameConfig.load(file.getAbsolutePath());
        assertTrue(config.isEnabled());
        assertEquals("http://remote:9000", config.getMcpUrl());
    }

    @Test
    void load_emptyMcpUrl_fallsBackToDefault() throws Exception {
        File file = new File(tempDir, "autogame-config.json");
        write(file, "{\"enabled\":true,\"mcpUrl\":\"\"}");
        AutogameConfig config = AutogameConfig.load(file.getAbsolutePath());
        assertEquals("http://localhost:8765", config.getMcpUrl());
    }

    @Test
    void load_invalidJson_returnsDefaults() throws Exception {
        File file = new File(tempDir, "autogame-config.json");
        write(file, "{not valid json");
        AutogameConfig config = AutogameConfig.load(file.getAbsolutePath());
        assertFalse(config.isEnabled()); // 解析失败回退默认
        assertEquals("http://localhost:8765", config.getMcpUrl());
    }

    private void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        w.write(content);
        w.close();
    }
}
