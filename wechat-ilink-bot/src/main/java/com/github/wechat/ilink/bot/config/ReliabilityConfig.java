package com.github.wechat.ilink.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 可靠性参数配置（Phase 5）：发送重试、限流、会话刷盘、MCP 健康监控。
 *
 * 文件位置：{@code data/reliability-config.json}。缺省即生成模板并使用默认值。
 * 默认值保持既有行为：发送同步立即落盘（{@code flushDelayMs=0}），仅启用周期兜底 flush。
 * 把 {@code flushDelayMs} 调大于 0 即启用突发写合并（debounce）。
 */
public class ReliabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private int sendMaxAttempts = 3;
    private long sendBackoffMs = 500L;
    private int rateLimitPerMin = 30;
    private long rateLimitWindowMs = 60_000L;
    private long flushDelayMs = 0L;
    private long flushIntervalMs = 30_000L;
    private long mcpHealthIntervalMs = 30_000L;
    private int mcpToolRefreshTicks = 2;

    public ReliabilityConfig() {
    }

    public static ReliabilityConfig load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            createTemplate(file);
            log.info("Reliability 配置未找到，已生成模板，使用默认值: {}", filePath);
            return new ReliabilityConfig();
        }
        try {
            ReliabilityConfig config = MAPPER.readValue(file, ReliabilityConfig.class);
            log.info("Reliability 配置已加载: sendAttempts={}, rateLimit={}/{}ms, flushDelay={}ms, mcpHealth={}ms",
                    config.getSendMaxAttempts(), config.getRateLimitPerMin(), config.getRateLimitWindowMs(),
                    config.getFlushDelayMs(), config.getMcpHealthIntervalMs());
            return config;
        } catch (IOException e) {
            log.error("Reliability 配置读取失败: {}", filePath, e);
            return new ReliabilityConfig();
        }
    }

    private static void createTemplate(File file) {
        try {
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(new ReliabilityConfig()));
            writer.close();
            log.info("已创建 Reliability 配置模板: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.warn("无法创建 Reliability 配置模板", e);
        }
    }

    public int getSendMaxAttempts() { return sendMaxAttempts; }
    public void setSendMaxAttempts(int sendMaxAttempts) { this.sendMaxAttempts = sendMaxAttempts; }
    public long getSendBackoffMs() { return sendBackoffMs; }
    public void setSendBackoffMs(long sendBackoffMs) { this.sendBackoffMs = sendBackoffMs; }
    public int getRateLimitPerMin() { return rateLimitPerMin; }
    public void setRateLimitPerMin(int rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; }
    public long getRateLimitWindowMs() { return rateLimitWindowMs; }
    public void setRateLimitWindowMs(long rateLimitWindowMs) { this.rateLimitWindowMs = rateLimitWindowMs; }
    public long getFlushDelayMs() { return flushDelayMs; }
    public void setFlushDelayMs(long flushDelayMs) { this.flushDelayMs = flushDelayMs; }
    public long getFlushIntervalMs() { return flushIntervalMs; }
    public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    public long getMcpHealthIntervalMs() { return mcpHealthIntervalMs; }
    public void setMcpHealthIntervalMs(long mcpHealthIntervalMs) { this.mcpHealthIntervalMs = mcpHealthIntervalMs; }
    public int getMcpToolRefreshTicks() { return mcpToolRefreshTicks; }
    public void setMcpToolRefreshTicks(int mcpToolRefreshTicks) { this.mcpToolRefreshTicks = mcpToolRefreshTicks; }
}
