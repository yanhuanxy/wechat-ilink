package com.github.wechat.ilink.bot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.bot.config.LlmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiProviderTest {

    private OpenAiProvider provider;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LlmConfig config = new LlmConfig();
        config.setBaseUrl("https://api.example.com/v1");
        config.setApiKey("test-key");
        config.setModel("gpt-3.5-turbo");
        config.setMaxTokens(500);
        provider = new OpenAiProvider(config);
    }

    @Test
    void buildRequestBody_includesModelAndMessages() throws Exception {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system("You are helpful"));
        messages.add(ChatMessage.user("Hello"));

        String body = provider.buildRequestBody(messages);
        JsonNode root = MAPPER.readTree(body);

        assertEquals("gpt-3.5-turbo", root.get("model").asText());
        assertEquals(500, root.get("max_tokens").asInt());
        assertEquals(2, root.get("messages").size());
        assertEquals("system", root.get("messages").get(0).get("role").asText());
        assertEquals("You are helpful", root.get("messages").get(0).get("content").asText());
        assertEquals("user", root.get("messages").get(1).get("role").asText());
        assertEquals("Hello", root.get("messages").get(1).get("content").asText());
    }

    @Test
    void parseResponse_validJson_returnsContent() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi there!\"}}]}";
        String result = provider.parseResponse(response);
        assertEquals("Hi there!", result);
    }

    @Test
    void parseResponse_nullInput_returnsNull() throws Exception {
        assertNull(provider.parseResponse(null));
    }

    @Test
    void parseResponse_emptyChoices_returnsNull() throws Exception {
        String response = "{\"choices\":[]}";
        assertNull(provider.parseResponse(response));
    }

    @Test
    void chat_sendRequestFails_returnsNull() {
        LlmConfig config = new LlmConfig();
        config.setBaseUrl("http://localhost:1");
        config.setApiKey("key");
        config.setModel("model");
        config.setMaxTokens(10);
        config.setTimeoutMs(100);
        OpenAiProvider failingProvider = new OpenAiProvider(config);

        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.user("test"));
        assertNull(failingProvider.chat(messages));
    }

    @Test
    void buildStreamRequestBody_includesStreamTrue() throws Exception {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.user("Hello"));

        String body = provider.buildStreamRequestBody(messages);
        JsonNode root = MAPPER.readTree(body);

        assertTrue(root.get("stream").asBoolean());
        assertEquals("gpt-3.5-turbo", root.get("model").asText());
        assertEquals(1, root.get("messages").size());
    }

    @Test
    void buildRequestBody_noStreamField() throws Exception {
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.user("Hello"));

        String body = provider.buildRequestBody(messages);
        JsonNode root = MAPPER.readTree(body);

        assertNull(root.get("stream"));
    }
}
