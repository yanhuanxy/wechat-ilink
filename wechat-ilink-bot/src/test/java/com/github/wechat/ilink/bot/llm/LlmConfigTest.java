package com.github.wechat.ilink.bot.llm;

import com.github.wechat.ilink.bot.config.LlmConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigTest {

    @TempDir
    File tempDir;

    @Test
    void load_fileNotExists_createsTemplate() {
        String path = new File(tempDir, "sub/llm-config.json").getAbsolutePath();
        LlmConfig config = LlmConfig.load(path);

        assertFalse(config.isEnabled());
        assertTrue(new File(path).exists());
        assertEquals("openai", config.getProvider());
    }

    @Test
    void load_validFile_readsConfig() throws Exception {
        File file = new File(tempDir, "llm-config.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"provider\":\"openai\",\"apiKey\":\"sk-test\",\"baseUrl\":\"https://api.deepseek.com/v1\",\"model\":\"deepseek-chat\",\"maxTokens\":1000,\"timeoutMs\":60000,\"maxHistory\":30}");
        writer.close();

        LlmConfig config = LlmConfig.load(file.getAbsolutePath());

        assertTrue(config.isEnabled());
        assertEquals("sk-test", config.getApiKey());
        assertEquals("https://api.deepseek.com/v1", config.getBaseUrl());
        assertEquals("deepseek-chat", config.getModel());
        assertEquals(1000, config.getMaxTokens());
        assertEquals(60000, config.getTimeoutMs());
        assertEquals(30, config.getMaxHistory());
    }

    @Test
    void load_placeholderApiKey_notEnabled() throws Exception {
        File file = new File(tempDir, "llm-config.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"provider\":\"openai\",\"apiKey\":\"your-api-key-here\"}");
        writer.close();

        LlmConfig config = LlmConfig.load(file.getAbsolutePath());
        assertFalse(config.isEnabled());
    }

    @Test
    void load_emptyApiKey_notEnabled() throws Exception {
        File file = new File(tempDir, "llm-config.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"provider\":\"openai\",\"apiKey\":\"\"}");
        writer.close();

        LlmConfig config = LlmConfig.load(file.getAbsolutePath());
        assertFalse(config.isEnabled());
    }

    @Test
    void load_invalidJson_returnsDefaults() throws Exception {
        File file = new File(tempDir, "llm-config.json");
        FileWriter writer = new FileWriter(file);
        writer.write("not valid json");
        writer.close();

        LlmConfig config = LlmConfig.load(file.getAbsolutePath());
        assertEquals("openai", config.getProvider());
        assertEquals("gpt-3.5-turbo", config.getModel());
    }
}
