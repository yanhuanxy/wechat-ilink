# GameBot 设计

## 职责

`GameBot` 是 SDK 和模式路由器之间的桥梁：
1. 实现 `OnMessageListener`，接收 SDK 批量消息
2. 实现 `ModeSender`，作为所有 Mode 的消息发送通道（出向）
3. 实现 `MediaDownloader`，作为入向媒体下载通道（CLAUDE 模式文件回传）
4. 把每条消息委托给 `ModeRouter.route()` 路由到对应 BotMode
5. 发送响应给用户（catch-all 异常兜底）

> **路由规则与各 Mode 实现细节见 [mode-router.md](mode-router.md)。**

## 核心实现

```java
public class GameBot implements OnMessageListener, ModeSender, MediaDownloader {
    private volatile ILinkClient client;
    private final ModeRouter router;
    private final GameEngine engine;
    private final ResponseRenderer renderer;

    // 规范构造器（BotInstance 实际调用）—— 14 参，末位 ReliabilityConfig
    public GameBot(GameEngine engine, ResponseRenderer renderer,
                   LlmProvider llmProvider, ChatHistoryManager chatHistory,
                   SessionManager sessions, boolean streamingEnabled,
                   int typingIntervalMs, LlmRequestQueue llmQueue,
                   TaskMessageHandler taskHandler, ClaudeBridgeMode claudeMode,
                   ClaudeSessionRepository claudeSessionRepo,
                   McpClient mcpClient, McpToolRegistry mcpToolRegistry,
                   ReliabilityConfig reliability) {
        this.engine = engine;
        this.renderer = renderer;
        // sender 经 RetrySender 装饰（Phase 5 发送重试）；this 同时作为 MediaDownloader
        ModeSender retrySender = new RetrySender(this,
                reliability.getSendMaxAttempts(), reliability.getSendBackoffMs());
        ModeContext ctx = new ModeContext(retrySender, this, engine, renderer, llmProvider, chatHistory,
                llmQueue, sessions, taskHandler, claudeSessionRepo, streamingEnabled, typingIntervalMs,
                mcpClient, mcpToolRegistry);
        AutogameMode autogameMode = mcpClient != null ? new AutogameMode() : null;
        RateLimiter rateLimiter = new RateLimiter(reliability.getRateLimitPerMin(), reliability.getRateLimitWindowMs());
        rateLimiter.startCleanup();
        this.router = new ModeRouter(ctx, new ChatMode(), new FarmMode(), new SystemCommandMode(),
                new ReviewMode(taskHandler), claudeMode, autogameMode, rateLimiter);
    }

    public void setClient(ILinkClient client) { this.client = client; }

    @Override
    public void onMessages(List<WeixinMessage> messages) {
        for (WeixinMessage msg : messages) {
            try {
                ModeOutcome outcome = router.route(msg);
                if (outcome.getStatus() == HANDLED && outcome.getErrorMessage() != null) {
                    client.sendText(msg.getFrom_user_id(), outcome.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("消息处理出错, userId={}", msg.getFrom_user_id(), e);
                // catch-all 兜底
            }
        }
    }

    // ModeSender 实现 — 所有 BotMode 通过这里发消息，不直接持有 ILinkClient
    @Override
    public void sendText(String userId, String text) throws IOException {
        MessageAuditLog.outbound(userId, "text", text);   // 出向审计落 logs/io/<userId>/
        client.sendText(userId, text);
    }
    // sendTextWithTyping / sendImage / sendFile / sendVideo 同理：先 outbound 审计再委托 client；startTyping / stopTyping 直接委托

    // MediaDownloader 实现 — 入向媒体下载，委托 SDK 完成 CDN 下载 + AES 解密
    @Override
    public byte[] downloadImage(MessageItem item) throws IOException { return client.downloadImageFromMessageItem(item); }
    @Override
    public byte[] downloadFile(MessageItem item) throws IOException { return client.downloadFileFromMessageItem(item); }
}
```

> `GameBot` 由 `BotInstance` 构造（多实例 + 登录重试）；存在多个重载构造器向后兼容测试，规范路径走 14 参构造器。`client` 经 `setClient()` 延迟注入（SDK 客户端构建需 `GameBot` 作监听器）。

## 消息提取

`ModeRouter` 内置静态 `extractText(WeixinMessage)`，从 SDK 多层结构里取文本：

```
WeixinMessage
  └── getItem_list() → List<MessageItem>
        └── getText_item() → TextItem
              └── getText() → String（实际文本内容）
```

非文本消息（图片、语音、视频等）`getText_item()` 返回 null，但视频消息通过 `getVideo_item()` 单独识别（抢占式路由到 ReviewMode）。

## 消息路由（委托给 ModeRouter）

GameBot 不再自己分流消息，全部交给 `ModeRouter` 按优先级处理：

| 优先级 | 条件 | 路由目标 |
|-------|------|---------|
| — | `RateLimiter.tryAcquire` 超限（Phase 5） | sendSafe("请求过于频繁…") |
| 1 | 消息含 `video_item` | ReviewMode.handleVideo（抢占式） |
| 2 | 当前 CLAUDE 且含 image/file_item | handleClaudeFileIntake → BridgeFileBuffer（Phase 4） |
| 3 | 文本为空 | SKIP |
| 4 | `#` 前缀 | FarmMode（剥离 `#`） |
| 5 | `/` 前缀 | SystemCommandMode（`/mode`/`/new`/`/sessions`/`/resume`/`/help`/`/status`） |
| 6 | `!` 前缀（autogame 启用） | AutogameMode → MCP |
| 7 | 60s 内有视频票据 | ReviewMode.handlePendingPrompt |
| 8 | 当前模式（默认 ChatMode） | session.currentMode 对应的 Mode |

详细流程见 [data-flow.md](../architecture/data-flow.md) 与 [mode-router.md](mode-router.md)。

## 错误处理

| 路径 | 错误消息 |
|------|---------|
| GameBot catch-all | "出了点问题，输入 /help 查看可用命令" |
| FarmMode | "出了点问题，输入'#帮助'查看可用命令" |
| ChatMode（LLM 错误） | "AI 暂时无法回复，请稍后重试" |
| ChatMode（队列拒绝） | "AI 当前繁忙，请稍后重试" |

所有异常被捕获，确保不会因单个消息处理失败影响其他消息。错误发送本身也包裹在 try-catch 中。

## 设计要点

- `GameBot` 通过 `setClient()` 接收 `ILinkClient`（因客户端构建需要 GameBot 作为监听器，无法构造器注入）
- `GameBot` 同时实现 `ModeSender`（出向）与 `MediaDownloader`（入向），让 Mode 经接口回调，避免直接持有 SDK
- `ModeContext.sender` 是 `new RetrySender(this, ...)` 装饰器（Phase 5 发送重试）；`downloader` 是 `this`（GameBot 本身）——构造期用 `this` 填充是项目唯一允许的引用泄漏例外
- `#` 前缀在 `ModeRouter` 中剥离，`FarmMode`/`CommandParser` 收到的文本不含 `#`
- 命令路径：ModeRouter → FarmMode → GameEngine → Command，返回后由 sender 发送
- 聊天路径：ModeRouter → ChatMode（直接处理，不经过 GameEngine）
- 视频路径：ModeRouter → ReviewMode → TaskMessageHandler
- 系统命令路径：ModeRouter → SystemCommandMode（`/` 前缀）
- autogame 路径：ModeRouter → AutogameMode → `McpClient.callTool`（`!` 前缀）
- CLAUDE 文件回传路径：ModeRouter.handleClaudeFileIntake → `downloader.downloadImage/File` → BridgeFileBuffer（入向）；产物经 `sender.sendImage/File/Video` 回发（出向）
- `LlmRequestQueue` 提供 LLM 调用的背压机制：有界线程池（3 线程）+ 每用户并发限制（同一用户同时仅 1 个请求）
- 队列满或用户已有请求时，立即返回"繁忙"提示，不阻塞 SDK 消息线程
- `sendText(userId, text)` 只需 2 个参数，无需 contextToken
- per-user 锁下沉到 `SessionManager.lockFor(userId)`（Phase 5）；命令处理在该锁内，`sendText` 在锁外，不同用户消息并行
- 落盘经 `sessions.scheduleFlush(userId)`（默认同步立即刷；非旧的 `saveSession`）
- `onMessages` 检查 `outcome.getErrorMessage()`：HANDLED 但带错误消息时补发该消息；任何异常 catch-all 兜底为"出了点问题，请稍后再试"
- **消息去重**：`onMessages` 处理每条消息前先经 `MessageDedupRepository` 判 `message_id ≤ 用户水位线` → 跳过（重启后 SDK resume 重投的旧消息，避免旧问题被重跑）；处理后 `markProcessed` 上移水位线。`message_id` 为 null 或 `dedup=null` 时不去重（详见 [reliability.md](reliability.md) G9）
- **收发审计**：出向在 `ModeSender` 各 `send*` 方法调 `MessageAuditLog.outbound`，入向在 `ModeRouter.route` 调 `MessageAuditLog.inbound`；经 MDC `userId` + SiftingAppender 落到 `logs/io/<userId>/io.<date>.log`（详见 [logging.md](logging.md)）
