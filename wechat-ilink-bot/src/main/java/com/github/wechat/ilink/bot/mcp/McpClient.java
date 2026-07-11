package com.github.wechat.ilink.bot.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.HttpConnectStrategy;
import com.launchdarkly.eventsource.MessageEvent;
import com.launchdarkly.eventsource.background.BackgroundEventHandler;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 最小 MCP 客户端：JSON-RPC 2.0 over HTTP + SSE。
 *
 * 协议（基于 mcp.server.sse.SseServerTransport）：
 *   1. GET  /sse           —— 建立 SSE 长连接
 *   2. SSE event "endpoint" data:/messages/?session_id=xxx
 *   3. POST /messages/?session_id=xxx  body=JSON-RPC request → HTTP 202
 *   4. SSE event "message"  data=JSON-RPC response（带 id）
 *
 * 线程模型：
 *   - SSE EventSource 在自己的线程回调 EventHandler
 *   - 调用方线程 POST 请求；CompletableFuture 匹配 id 完成响应
 *   - pending map 用 ConcurrentHashMap 保证并发安全
 *
 * 可靠性（Phase 5）：
 *   - {@link #isConnected()} 同时要求 SSE 存活（sseAlive），避免"endpoint 已收到但 SSE 已断"的假真。
 *   - {@link #reconnect()} 关旧 SSE、重置握手状态后重连（复用同一实例，避免替换外部 final 引用）。
 *   - 请求超时或 POST 失败时清理 pending，防内存泄漏。
 *
 * 不实现：cancellation、resource（只关心 tools）、sampling。
 *
 * 鉴权（迭代C）：authToken 非空时 SSE 连接与所有 POST 请求带 {@code Authorization: Bearer <token>}。
 */
public final class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** MCP 2024-11-05 协议版本（Python mcp 1.x 支持）。 */
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final String baseUrl;
    private final String authToken;
    private final OkHttpClient http;
    private BackgroundEventSource sse;

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pending =
            new ConcurrentHashMap<Integer, CompletableFuture<JsonNode>>();
    private final AtomicReference<String> postEndpoint = new AtomicReference<String>();
    private CompletableFuture<Void> endpointReady = new CompletableFuture<Void>();

    private volatile boolean closed = false;
    private volatile boolean sseAlive = false;

    public McpClient(String baseUrl) {
        this(baseUrl, null);
    }

    /**
     * @param authToken 非空时随 SSE 连接 + POST 请求带 {@code Authorization: Bearer <token>}；为空则不鉴权（本地开发兼容）
     */
    public McpClient(String baseUrl, String authToken) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.authToken = (authToken == null || authToken.isEmpty()) ? null : authToken;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.sse = buildSse();
    }

    /** 建立 SSE 连接并等待 endpoint 事件。幂等。 */
    public synchronized void connect() throws IOException {
        if (closed) {
            throw new IOException("McpClient closed");
        }
        if (postEndpoint.get() != null && sseAlive) {
            return;
        }
        sse.start();
        try {
            endpointReady.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            throw new IOException("MCP server did not send endpoint event within 15s: " + baseUrl, te);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for MCP endpoint", ie);
        } catch (ExecutionException ee) {
            throw new IOException("MCP SSE channel failed", ee.getCause());
        }
    }

    /** 关旧 SSE、重置握手状态后重建连接 + initialize。供健康监控在断线后调用。 */
    public synchronized void reconnect() throws IOException {
        if (closed) {
            throw new IOException("McpClient closed");
        }
        log.info("MCP reconnecting: {}", baseUrl);
        closeSseQuietly();
        sseAlive = false;
        postEndpoint.set(null);
        endpointReady = new CompletableFuture<Void>();
        this.sse = buildSse();
        connect();
        initialize();
    }

    /** 发送 initialize 握手 + notifications/initialized 通知。 */
    public void initialize() throws IOException {
        Map<String, Object> clientInfo = new HashMap<String, Object>();
        clientInfo.put("name", "wechat-ilink-bot");
        clientInfo.put("version", "1.0");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Collections.<String, Object>emptyMap());
        params.put("clientInfo", clientInfo);

        JsonNode result = await(sendRequest("initialize", params), 30, "initialize");
        log.info("MCP initialized: server={}", result == null ? "?" : result.path("serverInfo"));
        sendNotification("notifications/initialized", Collections.<String, Object>emptyMap());
    }

    /** 调 tools/list。 */
    public List<McpTool> listTools() throws IOException {
        JsonNode result = await(sendRequest("tools/list", Collections.<String, Object>emptyMap()), 30, "tools/list");
        return parseTools(result);
    }

    /** 调 tools/call。超时 10 分钟（兼容长任务如模板执行）。 */
    public McpToolResult callTool(String name, Map<String, Object> arguments) throws IOException {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", name);
        params.put("arguments", arguments == null ? Collections.<String, Object>emptyMap() : arguments);
        JsonNode result = await(sendRequest("tools/call", params), 600, "tools/call(" + name + ")");
        return parseToolResult(result);
    }

    public void close() {
        closed = true;
        closeSseQuietly();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
        for (Map.Entry<Integer, CompletableFuture<JsonNode>> e : pending.entrySet()) {
            e.getValue().completeExceptionally(new IOException("McpClient closed"));
        }
        pending.clear();
    }

    /** 连接可用：SSE 存活 + 已收到 POST endpoint + 未关闭。 */
    public boolean isConnected() {
        return sseAlive && postEndpoint.get() != null && !closed;
    }

    /** 当前在途（已 POST、尚未收到 SSE 响应）的请求数，用于观测队头阻塞。 */
    public int pendingCount() {
        return pending.size();
    }

    // ---------------- SSE 生命周期（包级可见，便于单测模拟） ----------------

    void onSseOpen() {
        sseAlive = true;
        log.info("MCP SSE open: {}", baseUrl);
    }

    void onSseClosed() {
        sseAlive = false;
        log.info("MCP SSE closed");
    }

    void onSseError(Throwable t) {
        sseAlive = false;
        if (!closed) {
            log.warn("MCP SSE error: {}", t.getMessage());
        }
    }

    // ---------------- 内部 ----------------

    private void closeSseQuietly() {
        try {
            if (sse != null) {
                sse.close();
            }
        } catch (Exception e) {
            log.warn("Failed to close SSE: {}", e.getMessage());
        }
    }

    private BackgroundEventSource buildSse() {
        final McpClient self = this;
        BackgroundEventHandler handler = new BackgroundEventHandler() {
            @Override
            public void onOpen() {
                self.onSseOpen();
            }

            @Override
            public void onClosed() {
                self.onSseClosed();
            }

            @Override
            public void onMessage(String event, MessageEvent messageEvent) {
                self.handleSseEvent(event, messageEvent.getData());
            }

            @Override
            public void onComment(String comment) {
                // no-op
            }

            @Override
            public void onError(Throwable t) {
                self.onSseError(t);
            }
        };
        URI uri = URI.create(this.baseUrl + "/sse");
        HttpConnectStrategy connectStrategy = ConnectStrategy.http(uri);
        if (authToken != null) {
            connectStrategy = connectStrategy.header("Authorization", "Bearer " + authToken);
        }
        EventSource.Builder esBuilder = new EventSource.Builder(connectStrategy);
        return new BackgroundEventSource.Builder(handler, esBuilder).build();
    }

    void handleSseEvent(String event, String data) {
        if ("endpoint".equals(event)) {
            String full = data.startsWith("http")
                    ? data
                    : this.baseUrl + (data.startsWith("/") ? "" : "/") + data;
            postEndpoint.set(full);
            endpointReady.complete(null);
            log.info("MCP endpoint received: {}", full);
            return;
        }
        if (!"message".equals(event)) {
            return;
        }
        try {
            JsonNode node = MAPPER.readTree(data);
            JsonNode idNode = node.get("id");
            if (idNode == null || !idNode.isIntegralNumber()) {
                return;
            }
            int id = idNode.asInt();
            CompletableFuture<JsonNode> f = pending.remove(id);
            if (f == null) {
                return;
            }
            JsonNode error = node.get("error");
            if (error != null) {
                f.completeExceptionally(new IOException("JSON-RPC error: " + error));
            } else {
                f.complete(node.get("result"));
            }
        } catch (Exception e) {
            log.warn("Failed to parse MCP message: {}", e.getMessage());
        }
    }

    private CompletableFuture<JsonNode> sendRequest(String method, Map<String, Object> params) throws IOException {
        String endpoint = postEndpoint.get();
        if (endpoint == null) {
            throw new IOException("MCP not connected (no POST endpoint; call connect() first)");
        }
        int id = nextId.getAndIncrement();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.set("params", MAPPER.valueToTree(params));

        CompletableFuture<JsonNode> future = new CompletableFuture<JsonNode>();
        pending.put(id, future);

        RequestBody rb = RequestBody.create(MAPPER.writeValueAsString(body), JSON);
        Request.Builder reqBuilder = new Request.Builder().url(endpoint).post(rb);
        if (authToken != null) {
            reqBuilder.header("Authorization", "Bearer " + authToken);
        }
        Request req = reqBuilder.build();
        try (Response resp = http.newCall(req).execute()) {
            int code = resp.code();
            // 202 Accepted：server 收到，响应走 SSE
            if (code != 202 && code != 200) {
                ResponseBody rb2 = resp.body();
                String text = rb2 != null ? rb2.string() : "";
                pending.remove(id);
                future.completeExceptionally(
                        new IOException("POST " + method + " failed: HTTP " + code + " " + text));
            }
        } catch (IOException e) {
            // POST 本身失败（连接拒绝等）：清理在途票据，避免泄漏
            pending.remove(id);
            throw e;
        }
        return future;
    }

    private void sendNotification(String method, Map<String, Object> params) throws IOException {
        String endpoint = postEndpoint.get();
        if (endpoint == null) {
            throw new IOException("MCP not connected");
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.set("params", MAPPER.valueToTree(params));
        RequestBody rb = RequestBody.create(MAPPER.writeValueAsString(body), JSON);
        Request.Builder reqBuilder = new Request.Builder().url(endpoint).post(rb);
        if (authToken != null) {
            reqBuilder.header("Authorization", "Bearer " + authToken);
        }
        Request req = reqBuilder.build();
        try (Response resp = http.newCall(req).execute()) {
            if (resp.code() != 202 && resp.code() != 200) {
                log.warn("Notification {} returned HTTP {}", method, resp.code());
            }
        }
    }

    private JsonNode await(CompletableFuture<JsonNode> future, int timeoutSec, String label) throws IOException {
        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            // 超时：回收在途票据，防 pending 泄漏
            pending.values().remove(future);
            throw new IOException(label + " timed out (" + timeoutSec + "s)", te);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pending.values().remove(future);
            throw new IOException(label + " interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException(label + " failed: " + cause.getMessage(), cause);
        }
    }

    List<McpTool> parseTools(JsonNode result) {
        if (result == null) {
            return Collections.emptyList();
        }
        JsonNode tools = result.get("tools");
        if (tools == null || !tools.isArray()) {
            return Collections.emptyList();
        }
        List<McpTool> list = new ArrayList<McpTool>();
        for (JsonNode t : tools) {
            JsonNode nameNode = t.get("name");
            if (nameNode == null) {
                continue;
            }
            String name = nameNode.asText();
            String desc = t.has("description") ? t.get("description").asText() : "";
            JsonNode schema = t.get("inputSchema");
            list.add(new McpTool(name, desc, schema));
        }
        return list;
    }

    McpToolResult parseToolResult(JsonNode result) {
        if (result == null) {
            return new McpToolResult(false, "");
        }
        boolean isError = result.has("isError") && result.get("isError").asBoolean();
        JsonNode content = result.get("content");
        if (content == null || !content.isArray() || content.size() == 0) {
            return new McpToolResult(isError, "");
        }
        JsonNode first = content.get(0);
        String text = first.has("text") ? first.get("text").asText() : first.toString();
        return new McpToolResult(isError, text);
    }
}
