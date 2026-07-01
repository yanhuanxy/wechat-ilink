package com.github.wechat.ilink.bot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 健康监控：定时探测 {@link McpClient#isConnected()}，断线则 {@link McpClient#reconnect()}；
 * 顺带周期刷新 {@link McpToolRegistry}（G3 自愈 + G7 tool 刷新）。
 *
 * 线程模型：单线程 daemon 调度器，健康检查/重连全在监控线程，不阻塞消息线程。
 * 重连失败不冒泡——记 warn，下个周期再试。
 */
public final class McpHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(McpHealthMonitor.class);

    private final McpClient client;
    private final McpToolRegistry registry;
    private final long healthIntervalMs;
    private final int toolRefreshTicks;
    private final AtomicInteger tick = new AtomicInteger(0);
    private ScheduledExecutorService executor;

    /**
     * @param client            被监控的 MCP 客户端
     * @param registry          tool 注册表（可为 null：跳过刷新）
     * @param healthIntervalMs  探测周期（ms）
     * @param toolRefreshTicks  每 N 个周期刷新一次 tool（≤0 表示不周期刷新）
     */
    public McpHealthMonitor(McpClient client, McpToolRegistry registry,
                            long healthIntervalMs, int toolRefreshTicks) {
        this.client = client;
        this.registry = registry;
        this.healthIntervalMs = healthIntervalMs;
        this.toolRefreshTicks = toolRefreshTicks;
    }

    public void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "mcp-health-monitor");
                t.setDaemon(true);
                return t;
            }
        });
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                runHealthCheck();
            }
        }, healthIntervalMs, healthIntervalMs, TimeUnit.MILLISECONDS);
        log.info("MCP 健康监控已启动: intervalMs={}, toolRefreshTicks={}", healthIntervalMs, toolRefreshTicks);
    }

    /** 单次健康检查（包级可见，便于单测直接驱动）。异常不向上抛。 */
    void runHealthCheck() {
        try {
            if (!client.isConnected()) {
                log.info("MCP 未连接，尝试重连");
                client.reconnect();
                refreshQuietly();
                return;
            }
            if (toolRefreshTicks > 0 && tick.incrementAndGet() % toolRefreshTicks == 0) {
                refreshQuietly();
            }
        } catch (Exception e) {
            log.warn("MCP 健康检查/重连失败，下个周期再试: {}", e.getMessage());
        }
    }

    private void refreshQuietly() {
        if (registry == null) {
            return;
        }
        try {
            registry.refresh();
        } catch (Exception e) {
            log.warn("MCP tool 刷新失败: {}", e.getMessage());
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
