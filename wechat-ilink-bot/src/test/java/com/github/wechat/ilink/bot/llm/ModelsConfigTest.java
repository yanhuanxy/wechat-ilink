package com.github.wechat.ilink.bot.llm;

import com.github.wechat.ilink.bot.config.LlmConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;

import static org.junit.jupiter.api.Assertions.*;

class ModelsConfigTest {

    @TempDir
    File tempDir;

    @Test
    void load_validFile_resolvesSharedProviderAndPerFeatureModels() {
        File file = new File(tempDir, "models-config.json");
        writeJson(file, "{"
                + "\"providers\": {"
                + "  \"zhipu\": {\"baseUrl\": \"https://zhipu.example/v4\", \"apiKey\": \"zk-1\"},"
                + "  \"dashscope\": {\"baseUrl\": \"https://ds.example/v1\", \"apiKey\": \"sk-2\", \"uploadsUrl\": \"https://ds.example/uploads\"}"
                + "},"
                + "\"chat\": {\"provider\": \"zhipu\", \"type\": \"openai\", \"model\": \"glm-4-flash\", \"maxTokens\": 4096, \"maxHistory\": 10},"
                + "\"review\": {\"provider\": \"dashscope\", \"model\": \"qwen-omni\"},"
                + "\"bridge\": {\"provider\": \"dashscope\", \"model\": \"qwen-plus\"}"
                + "}");

        ModelsConfig config = ModelsConfig.load(file.getAbsolutePath());

        LlmConfig chat = config.resolveChatLlmConfig();
        assertEquals("openai", chat.getProvider());
        assertEquals("https://zhipu.example/v4", chat.getBaseUrl());
        assertEquals("zk-1", chat.getApiKey());
        assertEquals("glm-4-flash", chat.getModel());
        assertEquals(4096, chat.getMaxTokens());
        assertEquals(10, chat.getMaxHistory());

        assertEquals("qwen-omni", config.reviewModel());
        assertEquals("qwen-plus", config.bridgeModel());

        // Review 与 Bridge 共用同一份 DashScope provider（只定义一次）
        ModelsConfig.Provider ds = config.dashscope();
        assertEquals("https://ds.example/v1", ds.getBaseUrl());
        assertEquals("sk-2", ds.getApiKey());
        assertEquals("https://ds.example/uploads", ds.getUploadsUrl());
    }

    @Test
    void bridgeProvider_honorsDeclaredProvider_notHardcodedDashscope() {
        File file = new File(tempDir, "models-config.json");
        writeJson(file, "{"
                + "\"providers\": {"
                + "  \"dashscope\": {\"baseUrl\": \"https://ds.example/compatible-mode/v1\", \"apiKey\": \"sk-ds\"},"
                + "  \"anthropic\": {\"baseUrl\": \"https://ds.example/apps/anthropic\", \"apiKey\": \"sk-anthropic\"}"
                + "},"
                + "\"bridge\": {\"provider\": \"anthropic\", \"model\": \"qwen3.7-max\", \"smallModel\": \"qwen-flash\"}"
                + "}");

        ModelsConfig config = ModelsConfig.load(file.getAbsolutePath());

        ModelsConfig.Provider bridgeProvider = config.bridgeProvider();
        assertNotNull(bridgeProvider);
        assertEquals("https://ds.example/apps/anthropic", bridgeProvider.getBaseUrl());
        assertEquals("sk-anthropic", bridgeProvider.getApiKey());
        assertEquals("qwen3.7-max", config.bridgeModel());
        assertEquals("qwen-flash", config.bridgeSmallModel());
    }

    @Test
    void bridgeProvider_unknownProviderName_returnsNull() {
        File file = new File(tempDir, "models-config.json");
        writeJson(file, "{\"bridge\": {\"provider\": \"missing\", \"model\": \"m\"}}");

        ModelsConfig config = ModelsConfig.load(file.getAbsolutePath());

        assertNull(config.bridgeProvider());
    }

    @Test
    void resolveChatLlmConfig_unknownProvider_leavesCredentialsNull() {
        File file = new File(tempDir, "models-config.json");
        writeJson(file, "{\"chat\": {\"provider\": \"missing\", \"model\": \"m\"}}");

        ModelsConfig config = ModelsConfig.load(file.getAbsolutePath());
        LlmConfig chat = config.resolveChatLlmConfig();

        assertEquals("m", chat.getModel());
        // provider 未匹配：不注入 apiKey，Chat 因缺凭证视为未启用（baseUrl 保留 LlmConfig 默认值）
        assertNull(chat.getApiKey());
        assertFalse(chat.isEnabled());
    }

    @Test
    void load_missingFile_generatesTemplateWithDefaults() {
        File file = new File(tempDir, "models-config.json");
        assertFalse(file.exists());

        ModelsConfig config = ModelsConfig.load(file.getAbsolutePath());

        assertTrue(file.exists());
        assertEquals("qwen-omni", config.reviewModel());
        assertEquals("qwen-plus", config.bridgeModel());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1",
                config.dashscope().getBaseUrl());
    }

    @Test
    void load_corruptedFile_returnsEmptyDefaults() {
        File file = new File(tempDir, "models-config.json");
        writeJson(file, "not json");

        ModelsConfig config = ModelsConfig.load(file.getAbsolutePath());

        assertNotNull(config.dashscope());
        assertEquals("", config.reviewModel());
        assertEquals("", config.dashscope().getBaseUrl());
    }

    private void writeJson(File file, String content) {
        try {
            FileWriter w = new FileWriter(file);
            w.write(content);
            w.close();
        } catch (Exception e) {
            fail(e);
        }
    }
}
