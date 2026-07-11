package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.mcp.McpClient;
import com.github.wechat.ilink.bot.mcp.McpTool;
import com.github.wechat.ilink.bot.mcp.McpToolRegistry;
import com.github.wechat.ilink.bot.mcp.McpToolResult;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ! 前缀模式：调用 wechat-link-autogame-xcx 暴露的 MCP tools。
 *
 * 命令映射：
 *   !list           — list_templates
 *   !run &lt;name&gt;   — run_template（异步：先回执"已开始"，完成后回执结果）
 *   !status         — get_status
 *   !stop           — stop_execution
 *   !report         — get_report
 *   !help           — 渲染 registry 中的 tool 列表
 *
 * 异步模型：
 *   - 短 tool 同步调（list/status/stop/report 直接阻塞 handleText，但 MCP 通常 &lt; 100ms 返回）
 *   - run_template 在 daemon 线程池里调，避免阻塞消息循环（满足 bot "≤ 2s 返回" SLA）
 *   - 单线程池保证 run 串行（Python 端 scheduler 也串行，对应一致）
 */
public class AutogameMode implements BotMode {

    private static final Logger log = LoggerFactory.getLogger(AutogameMode.class);

    // 迭代C：发给 Python 端 MCP tool 的 caller 标识（帐号级，用于越权校验/status 归属），非微信 userId。
    private final String botName;

    private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "autogame-async-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    public AutogameMode(String botName) {
        this.botName = botName;
    }

    @Override
    public BotModeType type() {
        return BotModeType.AUTOGAME;
    }

    @Override
    public ModeOutcome handleText(ModeContext ctx, PlayerSession session, String text) {
        String userId = session.getUserId();
        ModeSender sender = ctx.sender();
        McpClient client = ctx.mcpClient();

        if (client == null || !client.isConnected()) {
            send(sender, userId, "MCP 服务未启用。请联系管理员启动 wechat-link-autogame-xcx 的远程服务。");
            return ModeOutcome.handled();
        }

        String body = text.substring(1).trim();
        if (body.isEmpty()) {
            sendHelp(ctx, userId);
            return ModeOutcome.handled();
        }

        String[] parts = body.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        try {
            if ("help".equals(cmd)) {
                sendHelp(ctx, userId);
            } else if ("list".equals(cmd)) {
                McpToolResult r = client.callTool("list_templates", callerArgs());
                send(sender, userId, "本地模板：\n" + r.getText());
            } else if ("run".equals(cmd)) {
                handleRun(ctx, userId, arg);
            } else if ("status".equals(cmd)) {
                McpToolResult r = client.callTool("get_status", callerArgs());
                send(sender, userId, "状态：\n" + r.getText());
            } else if ("stop".equals(cmd)) {
                McpToolResult r = client.callTool("stop_execution", callerArgs());
                send(sender, userId, r.getText());
            } else if ("report".equals(cmd)) {
                McpToolResult r = client.callTool("get_report", callerArgs());
                send(sender, userId, "上次执行报告：\n" + r.getText());
            } else {
                send(sender, userId, "未知命令：!" + cmd + "\n输入 !help 查看可用命令");
            }
        } catch (IOException e) {
            log.error("MCP tool call failed, userId={}, cmd={}", userId, cmd, e);
            send(sender, userId, "调用失败：" + e.getMessage());
        }
        return ModeOutcome.handled();
    }

    private void handleRun(final ModeContext ctx, final String userId, final String name) {
        if (name.isEmpty()) {
            send(ctx.sender(), userId, "用法：!run <模板名>\n例如：!run 签到");
            return;
        }
        send(ctx.sender(), userId, "▶ 开始执行模板：" + name);

        final McpClient client = ctx.mcpClient();
        final ModeSender sender = ctx.sender();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    Map<String, Object> args = callerArgs();
                    args.put("name", name);
                    McpToolResult r = client.callTool("run_template", args);
                    String reply = r.isError()
                            ? "✗ 执行失败：" + r.getText()
                            : "✓ 执行完成：" + r.getText();
                    send(sender, userId, reply);
                } catch (IOException e) {
                    log.error("run_template async failed, userId={}, name={}", userId, name, e);
                    send(sender, userId, "✗ 执行异常：" + e.getMessage());
                }
            }
        };
        asyncExecutor.submit(task);
    }

    /** 每次 tool 调用都带上调用方（bot 账号）标识，供 Python 端做 status 归属 / 越权校验。 */
    private Map<String, Object> callerArgs() {
        Map<String, Object> args = new HashMap<String, Object>();
        if (botName != null) {
            args.put("caller", botName);
        }
        return args;
    }

    private void sendHelp(ModeContext ctx, String userId) {
        McpToolRegistry registry = ctx.mcpToolRegistry();
        StringBuilder sb = new StringBuilder();
        sb.append("wechat-link-autogame-xcx 远程指令\n");
        sb.append("!list — 列出可用模板\n");
        sb.append("!run <名称> — 执行模板（异步）\n");
        sb.append("!status — 查询执行状态\n");
        sb.append("!stop — 请求停止\n");
        sb.append("!report — 查看上次报告\n");
        if (registry != null && registry.isLoaded()) {
            sb.append("\nMCP tools：\n");
            for (McpTool t : registry.all()) {
                sb.append("  ").append(t.getName()).append(" — ").append(t.getDescription()).append("\n");
            }
        }
        send(ctx.sender(), userId, sb.toString());
    }

    private void send(ModeSender sender, String userId, String text) {
        try {
            sender.sendText(userId, text);
        } catch (Exception e) {
            log.error("AutogameMode send failed, userId={}", userId, e);
        }
    }
}
