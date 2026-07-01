package com.github.wechat.ilink.bot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;

public class SseParser {

    private static final Logger log = LoggerFactory.getLogger(SseParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_SIGNAL = "[DONE]";

    private SseParser() {
    }

    public static void parse(BufferedReader reader, StreamCallback callback) {
        StringBuilder accumulated = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith(":")) {
                    continue;
                }

                if (!line.startsWith(DATA_PREFIX)) {
                    continue;
                }

                String payload = line.substring(DATA_PREFIX.length());

                if (DONE_SIGNAL.equals(payload)) {
                    callback.onComplete(accumulated.toString());
                    return;
                }

                String token = extractToken(payload);
                if (token != null && !token.isEmpty()) {
                    accumulated.append(token);
                    callback.onToken(token);
                }
            }

            callback.onComplete(accumulated.toString());
        } catch (IOException e) {
            String partial = accumulated.toString();
            if (!partial.isEmpty()) {
                log.warn("SSE 流中断，已接收部分内容: {} chars", partial.length());
            }
            callback.onError(e);
        }
    }

    private static String extractToken(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) {
                return null;
            }
            JsonNode content = delta.get("content");
            if (content == null) {
                return null;
            }
            return content.asText();
        } catch (Exception e) {
            log.warn("SSE 事件解析失败: {}", json, e);
            return null;
        }
    }
}
