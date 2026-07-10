package com.github.wechat.ilink.bot.mcp;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolRegistryTest {

    @Test
    void all_emptyByDefault() {
        McpToolRegistry registry = new McpToolRegistry(mock(McpClient.class));
        assertTrue(registry.all().isEmpty());
        assertFalse(registry.isLoaded());
    }

    @Test
    void refresh_populatesFromClient() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.listTools()).thenReturn(Arrays.asList(
                new McpTool("run_template", "执行模板", null)));
        McpToolRegistry registry = new McpToolRegistry(client);

        registry.refresh();

        assertEquals(1, registry.all().size());
        assertTrue(registry.isLoaded());
    }

    @Test
    void find_returnsMatchByName() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.listTools()).thenReturn(Arrays.asList(
                new McpTool("run_template", "执行", null),
                new McpTool("list_templates", "列出", null)));
        McpToolRegistry registry = new McpToolRegistry(client);
        registry.refresh();

        assertNotNull(registry.find("run_template"));
        assertEquals("list_templates", registry.find("list_templates").getName());
        assertNull(registry.find("nonexistent"));
    }

    @Test
    void refresh_failureKeepsOldCache() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.listTools()).thenReturn(Arrays.asList(new McpTool("t", "x", null)));
        McpToolRegistry registry = new McpToolRegistry(client);
        registry.refresh();
        assertEquals(1, registry.all().size());

        when(client.listTools()).thenThrow(new RuntimeException("connection lost"));
        registry.refresh(); // 失败，旧缓存保留

        assertEquals(1, registry.all().size());
    }

    @Test
    void all_returnsImmutableSnapshot() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.listTools()).thenReturn(Arrays.asList(new McpTool("t", "x", null)));
        McpToolRegistry registry = new McpToolRegistry(client);
        registry.refresh();

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().add(new McpTool("x", "y", null)));
    }
}
