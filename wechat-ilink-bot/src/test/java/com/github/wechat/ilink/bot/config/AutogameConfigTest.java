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
    void defaults_disabledWithLocalUrlAndNoToken() {
        AutogameConfig config = new AutogameConfig();

        assertFalse(config.isEnabled());
        assertEquals("http://localhost:8765", config.getMcpUrl());
        assertNull(config.getAuthToken());
    }

    @Test
    void load_missingFile_returnsDefaultsAndCreatesTemplate() {
        File missing = new File(tempDir, "nope/autogame-config.json");

        AutogameConfig config = AutogameConfig.load(missing.getAbsolutePath());

        assertFalse(config.isEnabled());
        assertNull(config.getAuthToken());
        assertTrue(missing.exists());
    }

    @Test
    void load_validFile_appliesAuthToken() throws Exception {
        File file = new File(tempDir, "autogame-config.json");
        FileWriter w = new FileWriter(file);
        w.write("{\"enabled\":true,\"mcpUrl\":\"http://10.0.0.5:8765\",\"authToken\":\"secret-token\"}");
        w.close();

        AutogameConfig config = AutogameConfig.load(file.getAbsolutePath());

        assertTrue(config.isEnabled());
        assertEquals("http://10.0.0.5:8765", config.getMcpUrl());
        assertEquals("secret-token", config.getAuthToken());
    }
}
