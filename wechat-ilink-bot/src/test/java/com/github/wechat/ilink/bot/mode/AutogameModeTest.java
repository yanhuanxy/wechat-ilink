package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.mcp.McpClient;
import com.github.wechat.ilink.bot.mcp.McpToolResult;
import com.github.wechat.ilink.bot.mcp.McpToolRegistry;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AutogameMode（! 指令 → MCP）覆盖：命令分发、MCP 调用成功/失败、未连接兜底、异步 run。
 * 该路径是 Phase 5 可靠性重点（MCP 调用经 RetrySender/RateLimiter/McpHealthMonitor 保护）。
 */
class AutogameModeTest {

    private ModeContext ctx(McpClient client, McpToolRegistry registry, ModeSender sender) {
        return ModeContext.builder()
                .sender(sender).mcpClient(client).mcpToolRegistry(registry).build();
    }

    @Test
    void handleText_mcpNotConnected_sendsUnavailable() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(false);
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!list");

        verify(sender).sendText(eq("u1"), contains("MCP 服务未启用"));
    }

    @Test
    void handleText_list_returnsTemplates() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.callTool(eq("list_templates"), anyMap())).thenReturn(new McpToolResult(false, "签到\n浇水"));
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!list");

        verify(sender).sendText(eq("u1"), contains("签到"));
    }

    @Test
    void handleText_callThrows_sendsFailure() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.callTool(eq("get_status"), anyMap())).thenThrow(new IOException("boom"));
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!status");

        verify(sender).sendText(eq("u1"), contains("调用失败"));
    }

    @Test
    void handleText_unknownCommand_sendsHint() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(true);
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!bogus");

        verify(sender).sendText(eq("u1"), contains("未知命令"));
    }

    @Test
    void handleText_emptyBody_sendsHelp() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(true);
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!");

        verify(sender).sendText(eq("u1"), contains("!run"));
    }

    @Test
    void handleText_run_asyncSendsResult() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.callTool(eq("run_template"), anyMap())).thenReturn(new McpToolResult(false, "ok"));
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!run 签到");

        verify(sender).sendText(eq("u1"), contains("开始执行模板"));
        verify(sender, timeout(2000)).sendText(eq("u1"), contains("执行完成"));
    }

    @Test
    void handleText_runAsyncError_sendsFailure() throws Exception {
        McpClient client = mock(McpClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.callTool(eq("run_template"), anyMap())).thenThrow(new IOException("down"));
        ModeSender sender = mock(ModeSender.class);

        new AutogameMode().handleText(ctx(client, null, sender), new PlayerSession("u1"), "!run 签到");

        verify(sender, timeout(2000)).sendText(eq("u1"), contains("执行异常"));
    }

    @Test
    void type_returnsAutogame() {
        assertEquals(BotModeType.AUTOGAME, new AutogameMode().type());
    }
}
