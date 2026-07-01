package com.github.wechat.ilink.bot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.wechat.ilink.bot.config.LlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpenAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmConfig config;

    public OpenAiProvider(LlmConfig config) {
        this.config = config;
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        try {
            String requestBody = buildRequestBody(messages);
            String response = sendRequest(requestBody);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            return null;
        }
    }

    String buildRequestBody(List<ChatMessage> messages) throws Exception {
        return buildRequestBody(messages, false);
    }

    String buildStreamRequestBody(List<ChatMessage> messages) throws Exception {
        return buildRequestBody(messages, true);
    }

    private String buildRequestBody(List<ChatMessage> messages, boolean stream) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.getModel());
        root.put("max_tokens", config.getMaxTokens());
        if (stream) {
            root.put("stream", true);
        }

        ArrayNode msgArray = MAPPER.createArrayNode();
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = MAPPER.createObjectNode();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
            msgArray.add(msgNode);
        }
        root.set("messages", msgArray);

        return MAPPER.writeValueAsString(root);
    }

    String sendRequest(String requestBody) throws Exception {
        String endpoint = config.getBaseUrl() + "/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        conn.setConnectTimeout(config.getTimeoutMs());
        conn.setReadTimeout(config.getTimeoutMs());
        conn.setDoOutput(true);

        byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errorBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                errorBody.append(line);
            }
            reader.close();
            log.error("LLM API 错误: code={}, body={}", code, errorBody.toString());
            return null;
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    String parseResponse(String response) throws Exception {
        if (response == null) return null;
        JsonNode root = MAPPER.readTree(response);
        JsonNode choices = root.get("choices");
        if (choices != null && !choices.isEmpty()) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.get("content") != null) {
                return message.get("content").asText();
            }
        }
        log.warn("LLM 响应格式异常: {}", response);
        return null;
    }

    @Override
    public void chatStream(List<ChatMessage> messages, StreamCallback callback) {
        try {
            String requestBody = buildStreamRequestBody(messages);
            HttpURLConnection conn = openConnection(requestBody);

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String errorBody = readErrorStream(conn);
                conn.disconnect();
                log.error("LLM streaming API 错误: code={}, body={}", code, errorBody);
                callback.onError(new RuntimeException("LLM API error: " + code));
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            try {
                SseParser.parse(reader, callback);
            } finally {
                reader.close();
                conn.disconnect();
            }
        } catch (Exception e) {
            log.error("LLM streaming 调用失败", e);
            callback.onError(e);
        }
    }

    private HttpURLConnection openConnection(String requestBody) throws Exception {
        String endpoint = config.getBaseUrl() + "/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        conn.setConnectTimeout(config.getTimeoutMs());
        conn.setReadTimeout(config.getTimeoutMs());
        conn.setDoOutput(true);

        byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        os.close();

        return conn;
    }

    private String readErrorStream(HttpURLConnection conn) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "unable to read error stream";
        }
    }
}
