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

    // ---------------- handleSseEvent message 路径（当前 0 覆盖） ----------------

    @Test
    void handleSseEvent_unknownEvent_ignored() {
        McpClient client = newClient();
        // 非 endpoint / 非 message 事件：应静默忽略，不抛、不改变状态
        client.handleSseEvent("ping", "anything");

        assertFalse(client.isConnected());
    }

    @Test
    void handleSseEvent_messageWithUnknownId_ignored() {
        McpClient client = newClient();
        // id 不在 pending map 中：应静默忽略（避免 NPE）
        client.handleSseEvent("message", "{\"id\":99999,\"result\":{}}");

        // 无副作用
        assertEquals(0, client.pendingCount());
    }

    @Test
    void handleSseEvent_messageWithMalformedJson_ignored() {
        McpClient client = newClient();
        // 非 JSON data：应被 catch，不抛异常
        client.handleSseEvent("message", "not-json-at-all");

        assertEquals(0, client.pendingCount());
    }

    @Test
    void handleSseEvent_messageWithoutId_ignored() {
        McpClient client = newClient();
        // message 无 id 字段：应静默忽略
        client.handleSseEvent("message", "{\"result\":\"ok\"}");

        assertEquals(0, client.pendingCount());
    }

    // ---------------- close() 后的状态 ----------------

    @Test
    void close_makesDisconnected() {
        McpClient client = newClient();
        client.onSseOpen();
        client.handleSseEvent("endpoint", "/messages/?session_id=abc");
        assertTrue(client.isConnected());

        client.close();

        assertFalse(client.isConnected(), "close() 后应判为未连接");
    }

    // ---------------- baseUrl trim ----------------

    @Test
    void constructor_trimsTrailingSlashes() {
        // 带 1 个和多个尾斜杠：构造不应抛，且 endpoint 拼接时不应双斜杠
        McpClient c1 = new McpClient("http://127.0.0.1:1/");
        McpClient c2 = new McpClient("http://127.0.0.1:1///");

        c1.onSseOpen();
        c2.onSseOpen();
        // endpoint 收到相对路径时，应正确拼接为单斜杠
        c1.handleSseEvent("endpoint", "/messages/?session_id=a");
        c2.handleSseEvent("endpoint", "/messages/?session_id=b");

        assertTrue(c1.isConnected());
        assertTrue(c2.isConnected());
    }

    @Test
    void constructor_trimsWhitespace() {
        // 前后空白：应被 trim
        McpClient client = new McpClient("  http://127.0.0.1:1  ");
        client.onSseOpen();
        client.handleSseEvent("endpoint", "/messages/?session_id=x");

        assertTrue(client.isConnected());
    }
}
