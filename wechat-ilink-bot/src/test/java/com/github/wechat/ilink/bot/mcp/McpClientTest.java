package com.github.wechat.ilink.bot.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpClient newClient() {
        // 构造不会发起网络连接（SSE 仅在 connect() 时 start）；端口 1 不会被连
        return new McpClient("http://127.0.0.1:1");
    }

    @Test
    void isConnected_initiallyFalse() {
        assertFalse(newClient().isConnected());
    }

    @Test
    void isConnected_afterOpenAndEndpoint_true() {
        McpClient client = newClient();

        client.onSseOpen();
        client.handleSseEvent("endpoint", "/messages/?session_id=abc");

        assertTrue(client.isConnected());
    }

    @Test
    void isConnected_requiresSseOpen_endpointAloneNotEnough() {
        McpClient client = newClient();

        client.handleSseEvent("endpoint", "/messages/?session_id=abc");

        assertFalse(client.isConnected(), "endpoint 已收到但 SSE 未 open，不应判为已连接");
    }

    @Test
    void isConnected_afterSseClosed_false() {
        McpClient client = newClient();
        client.onSseOpen();
        client.handleSseEvent("endpoint", "/messages/?session_id=abc");
        assertTrue(client.isConnected());

        client.onSseClosed();

        assertFalse(client.isConnected());
    }

    @Test
    void isConnected_afterSseError_false() {
        McpClient client = newClient();
        client.onSseOpen();
        client.handleSseEvent("endpoint", "/messages/?session_id=abc");

        client.onSseError(new RuntimeException("disconnected"));

        assertFalse(client.isConnected());
    }

    @Test
    void pendingCount_initiallyZero() {
        assertEquals(0, newClient().pendingCount());
    }

    @Test
    void parseToolResult_isErrorAndText() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"boom\"}]}");

        McpToolResult r = client.parseToolResult(node);

        assertTrue(r.isError());
        assertEquals("boom", r.getText());
    }

    @Test
    void parseToolResult_okText() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"done\"}]}");

        McpToolResult r = client.parseToolResult(node);

        assertFalse(r.isError());
        assertEquals("done", r.getText());
    }

    @Test
    void parseToolResult_nullResult_returnsEmpty() {
        McpClient client = newClient();

        McpToolResult r = client.parseToolResult(null);

        assertEquals("", r.getText());
    }

    @Test
    void parseTools_list() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"tools\":["
                + "{\"name\":\"list_templates\",\"description\":\"列模板\"},"
                + "{\"name\":\"run_template\",\"description\":\"跑模板\"}"
                + "]}");

        java.util.List<McpTool> tools = client.parseTools(node);

        assertEquals(2, tools.size());
        assertEquals("run_template", tools.get(1).getName());
    }

    @Test
    void parseTools_nullOrMissing_returnsEmpty() {
        McpClient client = newClient();

        assertTrue(client.parseTools(null).isEmpty());
        assertTrue(client.parseTools(MAPPER.createObjectNode()).isEmpty());
    }
}
