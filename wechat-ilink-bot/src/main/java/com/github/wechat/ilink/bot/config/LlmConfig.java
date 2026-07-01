package com.github.wechat.ilink.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private int maxTokens;
    private int timeoutMs;
    private int maxHistory;
    private boolean streamingEnabled;
    private int typingIntervalMs;

    public LlmConfig() {
        this.provider = "openai";
        this.baseUrl = "https://api.openai.com/v1";
        this.model = "gpt-3.5-turbo";
        this.maxTokens = 5000;
        this.timeoutMs = 30000;
        this.maxHistory = 20;
        this.streamingEnabled = true;
        this.typingIntervalMs = 5000;
    }

    public static LlmConfig load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            createTemplate(file);
            return new LlmConfig();
        }

        try {
            LlmConfig config = MAPPER.readValue(file, LlmConfig.class);
            log.info("LLM 配置已加载: provider={}", config.getProvider());
            return config;
        } catch (IOException e) {
            log.error("LLM 配置文件读取失败: {}", filePath, e);
            return new LlmConfig();
        }
    }

    private static void createTemplate(File file) {
        try {
            file.getParentFile().mkdirs();
            LlmConfig template = new LlmConfig();
            template.setApiKey("your-api-key-here");

            FileWriter writer = new FileWriter(file);
            writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(template));
            writer.close();
            log.info("已创建 LLM 配置模板: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.warn("无法创建 LLM 配置模板", e);
        }
    }

    public boolean isEnabled() {
        return apiKey != null
                && !apiKey.trim().isEmpty()
                && !"your-api-key-here".equals(apiKey.trim());
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getMaxHistory() { return maxHistory; }
    public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }

    public boolean isStreamingEnabled() { return streamingEnabled; }
    public void setStreamingEnabled(boolean streamingEnabled) { this.streamingEnabled = streamingEnabled; }
    public int getTypingIntervalMs() { return typingIntervalMs; }
    public void setTypingIntervalMs(int typingIntervalMs) { this.typingIntervalMs = typingIntervalMs; }
}
