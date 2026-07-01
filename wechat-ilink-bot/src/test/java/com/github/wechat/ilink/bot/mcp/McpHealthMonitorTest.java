package com.github.wechat.ilink.bot.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpHealthMonitorTest {

    @Test
    void runHealthCheck_disconnected_reconnectsAndRefreshes() throws Exception {
        McpClient client = mock(McpClient.class);
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(client.isConnected()).thenReturn(false);
        McpHealthMonitor monitor = new McpHealthMonitor(client, registry, 1000L, 0);

        monitor.runHealthCheck();

        verify(client).reconnect();
        verify(registry).refresh();
    }

    @Test
    void runHealthCheck_connected_doesNotReconnect() throws Exception {
        McpClient client = mock(McpClient.class);
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(client.isConnected()).thenReturn(true);
        McpHealthMonitor monitor = new McpHealthMonitor(client, registry, 1000L, 0);

        monitor.runHealthCheck();

        verify(client, never()).reconnect();
        verify(registry, never()).refresh();
    }

    @Test
    void runHealthCheck_connected_refreshesEveryNTicks() throws Exception {
        McpClient client = mock(McpClient.class);
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(client.isConnected()).thenReturn(true);
        McpHealthMonitor monitor = new McpHealthMonitor(client, registry, 1000L, 2);

        monitor.runHealthCheck(); // tick 1 → 不刷新
        monitor.runHealthCheck(); // tick 2 → 刷新

        verify(registry, times(1)).refresh();
    }

    @Test
    void runHealthCheck_reconnectThrows_doesNotPropagate() throws Exception {
        McpClient client = mock(McpClient.class);
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(client.isConnected()).thenReturn(false);
        doThrow(new RuntimeException("still down")).when(client).reconnect();
        McpHealthMonitor monitor = new McpHealthMonitor(client, registry, 1000L, 0);

        // 不抛——下个周期再试
        assertDoesNotThrow(() -> monitor.runHealthCheck());

        verify(client).reconnect();
    }

    @Test
    void runHealthCheck_nullRegistry_skipsRefreshWithoutNpe() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(false);
        McpHealthMonitor monitor = new McpHealthMonitor(client, null, 1000L, 0);

        assertDoesNotThrow(() -> monitor.runHealthCheck());

        verify(client).reconnect();
    }

    @Test
    void start_idempotentAndShutdownClean() {
        McpClient client = mock(McpClient.class);
        McpHealthMonitor monitor = new McpHealthMonitor(client, null, 1000L, 0);

        monitor.start();
        monitor.start(); // 第二次应无副作用
        monitor.shutdown(); // 不抛
        monitor.shutdown(); // 幂等
    }
}
