package com.github.wechat.ilink.bot.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP tool 元数据。
 *
 * 对应 MCP 协议 tools/list 返回的 Tool 对象：
 * - name：tool 调用名（如 "run_template"）
 * - description：自然语言描述，供 LLM 理解 tool 用途
 * - inputSchema：JSON Schema 描述参数
 */
public final class McpTool {

    private final String name;
    private final String description;
    private final JsonNode inputSchema;

    public McpTool(String name, String description, JsonNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }

    @Override
    public String toString() {
        return "McpTool{name='" + name + "', description='" + description + "'}";
    }
}
