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

    @Test
    void parseTools_entryMissingName_skipped() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"tools\":[{\"description\":\"no name\"},{\"name\":\"ok\"}]}");

        java.util.List<McpTool> tools = client.parseTools(node);

        assertEquals(1, tools.size());
        assertEquals("ok", tools.get(0).getName());
    }

    @Test
    void parseTools_missingDescriptionAndSchema_defaults() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"tools\":[{\"name\":\"bare\"}]}");

        java.util.List<McpTool> tools = client.parseTools(node);

        assertEquals(1, tools.size());
        assertEquals("", tools.get(0).getDescription());
        assertNull(tools.get(0).getInputSchema());
    }

    @Test
    void parseToolResult_emptyContent_returnsEmpty() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"content\":[]}");

        McpToolResult r = client.parseToolResult(node);

        assertEquals("", r.getText());
    }

    @Test
    void parseToolResult_contentWithoutText_fallsBackToString() throws Exception {
        McpClient client = newClient();
        JsonNode node = MAPPER.readTree("{\"content\":[{\"foo\":\"bar\"}]}");

        McpToolResult r = client.parseToolResult(node);

        assertTrue(r.getText().contains("foo")); // 无 text 字段，回退到节点 toString
    }

    @Test
    void handleSseEvent_unknownEvent_ignored() {
        McpClient client = newClient();
        client.handleSseEvent("foo", "bar");
        assertFalse(client.isConnected());
    }

    @Test
    void handleSseEvent_messageWithoutId_ignored() {
        newClient().handleSseEvent("message", "{\"jsonrpc\":\"2.0\",\"result\":{}}"); // 无 id
    }

    @Test
    void handleSseEvent_messageUnknownId_ignored() {
        newClient().handleSseEvent("message", "{\"id\":999,\"result\":{}}"); // pending 无 999
    }

    @Test
    void handleSseEvent_messageInvalidJson_ignored() {
        newClient().handleSseEvent("message", "not json {{{"); // 解析异常被吞
    }

    @Test
    void listTools_notConnected_throws() {
        McpClient client = newClient(); // postEndpoint 为 null
        assertThrows(java.io.IOException.class, client::listTools);
    }

    @Test
    void connect_afterClosed_throws() {
        McpClient client = newClient();
        client.close();
        assertThrows(java.io.IOException.class, client::connect);
    }

    @Test
    void reconnect_afterClosed_throws() {
        McpClient client = newClient();
        client.close();
        assertThrows(java.io.IOException.class, client::reconnect);
    }

    @Test
    void constructor_trailingSlashAndSpaces_trimmed() {
        McpClient client = new McpClient("  http://127.0.0.1:1///  ");
        assertFalse(client.isConnected()); // 构造器规整 baseUrl，不抛异常
    }

    // ---- message 响应路由（核心：SSE 响应 → 等待中的 future）----
    // pending 是 private，唯一不连网的途径是反射塞入 future；字段名耦合，重构改名需同步。

    @Test
    void handleSseEvent_messageRoutesResultToPendingFuture() throws Exception {
        McpClient client = newClient();
        java.util.concurrent.CompletableFuture<JsonNode> fut = new java.util.concurrent.CompletableFuture<>();
        pendingOf(client).put(7, fut);

        client.handleSseEvent("message", "{\"id\":7,\"result\":{\"ok\":true}}");

        JsonNode result = fut.get(1, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(result.path("ok").asBoolean());
    }

    @Test
    void handleSseEvent_messageErrorCompletesFutureExceptionally() throws Exception {
        McpClient client = newClient();
        java.util.concurrent.CompletableFuture<JsonNode> fut = new java.util.concurrent.CompletableFuture<>();
        pendingOf(client).put(8, fut);

        client.handleSseEvent("message", "{\"id\":8,\"error\":{\"code\":-32601}}");

        assertTrue(fut.isCompletedExceptionally());
    }

    @SuppressWarnings("unchecked")
    private java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.CompletableFuture<JsonNode>> pendingOf(McpClient client) throws Exception {
        java.lang.reflect.Field f = McpClient.class.getDeclaredField("pending");
        f.setAccessible(true);
        return (java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.CompletableFuture<JsonNode>>) f.get(client);
    }
}
