package com.github.wechat.ilink.bot.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP tool 注册表：缓存 tools/list 结果，定期刷新。
 *
 * 线程模型：
 * - 单实例，被 AutogameMode / ChatMode 共享
 * - AtomicReference 保证读一致性（list 不可变快照）
 * - 定期刷新（默认 60s）由后台线程触发，调用方不阻塞
 *
 * 启动时调用 refresh() 拉一次；之后定期刷新由调度线程负责（当前实现是手动 refresh，
 * 未来可加 ScheduledExecutorService）。
 */
public final class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final McpClient client;
    private final AtomicReference<List<McpTool>> toolsRef;

    public McpToolRegistry(McpClient client) {
        this.client = client;
        this.toolsRef = new AtomicReference<List<McpTool>>(Collections.<McpTool>emptyList());
    }

    /** 刷新缓存：调一次 tools/list。失败时保留旧缓存，记日志。 */
    public void refresh() {
        try {
            List<McpTool> fresh = client.listTools();
            toolsRef.set(Collections.unmodifiableList(new ArrayList<McpTool>(fresh)));
            log.info("MCP tools refreshed: count={}", fresh.size());
        } catch (Exception e) {
            log.warn("Failed to refresh MCP tools, keeping old cache: {}", e.getMessage());
        }
    }

    /** 当前缓存的 tools 快照（不可变）。 */
    public List<McpTool> all() {
        return toolsRef.get();
    }

    /** 按 name 查找；不存在返回 null。 */
    public McpTool find(String name) {
        for (McpTool t : toolsRef.get()) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        return null;
    }

    /** 是否已加载过 tool（启动后首次 refresh 成功后为 true）。 */
    public boolean isLoaded() {
        return !toolsRef.get().isEmpty();
    }
}
