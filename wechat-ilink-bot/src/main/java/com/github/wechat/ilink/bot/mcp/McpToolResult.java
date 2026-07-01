package com.github.wechat.ilink.bot.mcp;

/**
 * MCP tool 调用结果。
 *
 * 对应 tools/call 返回的 content[]。本项目 Python 端约定 tool 返回单个 TextContent，
 * 即 result.content[0].text 是 JSON 字符串。调用方自行反序列化。
 *
 * isError=true 表示 tool 内部抛了异常（如模板不存在），content 里的 text 是 {"error":...}。
 */
public final class McpToolResult {

    private final boolean isError;
    private final String text;

    public McpToolResult(boolean isError, String text) {
        this.isError = isError;
        this.text = text;
    }

    public boolean isError() {
        return isError;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "McpToolResult{isError=" + isError + ", text='" + text + "'}";
    }
}
