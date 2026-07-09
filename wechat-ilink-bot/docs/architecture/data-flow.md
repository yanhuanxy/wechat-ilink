# 数据流

## 消息处理主流程

```
微信用户发送文本/视频
    │
    ▼
SDK 长轮询 getUpdates()  ─── 接收消息
    │
    ▼
GameBot.onMessages(List<WeixinMessage>)  ─── SDK 批量回调 + 实现 ModeSender（出向）/ MediaDownloader（入向媒体，CLAUDE 模式文件回传）
    │
    ├── 遍历消息，调用 ModeRouter.route(msg)
    │
    ▼
=== ModeRouter 路由规则（按优先级）===
ModeRouter.route(msg)
    │
    ├── 1. msg 含 video_item？ → ReviewMode.handleVideo（抢占式，忽略当前模式）
    │
    ├── 2. extractText(text) 为空？ → SKIP
    │
    ├── 3. text 以 "#" 开头？ → FarmMode.handleText（剥离 # 前缀）
    │
    ├── 4. text 以 "/" 开头？ → SystemCommandMode.handleText
    │   └── /mode [chat|claude]  /new  /sessions  /resume  /help  /status
    │
    ├── 5. ReviewMode.handlePendingPrompt 命中（60s 内有视频票据）？ → 命中即消费
    │
    └── 6. session.currentMode 对应的模式（CHAT → ChatMode，CLAUDE → ClaudeBridgeMode）

=== 命令流（FarmMode）===
FarmMode.handleText
    │
    ├── engine.dispatch(userId, commandText)  ─── 走原有 GameEngine 流程
    │       │
    │       ▼
    │   GameEngine.dispatch 内部：
    │   ├── per-user 锁
    │   ├── CommandParser.parse
    │   ├── SessionManager.getOrCreate
    │   ├── session.touchActivity
    │   ├── CommandRegistry.find + execute → CommandResult
    │   └── sessionManager.saveSession
    │
    ├── result.data 含 IMAGE_DATA_KEY？→ sender.sendImage
    └── 否 → renderer.render → sender.sendText

=== LLM 聊天流（ChatMode，默认模式）===
ChatMode.handleText
    │
    ├── llmProvider == null？→ sender.sendText(userId, text)（原样回显）
    │
    ├── ChatMode.buildSystemPrompt(session)  ─── 注入玩家状态
    ├── chatHistory.addMessage(userId, "user", text)
    │
    ├── streamingEnabled？
    │   └── 是 → handleStreamingChat
    │   │   ├── sender.startTyping(userId)
    │   │   ├── llmQueue.submit(userId, task)
    │   │   │   └── 队列满或用户已有请求 → "AI 当前繁忙，请稍后重试"
    │   │   └── llmProvider.chatStream(messages, callback)
    │   │       └── onComplete → sender.sendText + sender.stopTyping
    │   └── 否 → handleSyncChat
    │       ├── llmQueue.submit(userId, task)
    │       └── llmProvider.chat(messages) → sender.sendTextWithTyping
    │
    └── chatHistory.addMessage(userId, "assistant", reply)

=== 系统命令流（SystemCommandMode，/ 前缀）===
SystemCommandMode.handleText
    │
    ├── /mode [chat|claude|farm|review]
    │   ├── chat → session.setCurrentMode(CHAT) + saveSession
    │   ├── claude → session.setCurrentMode(CLAUDE) + saveSession + 引导文案
    │   ├── farm → "通过 # 前缀直接使用"
    │   └── review → "通过上传视频自动触发"
    │
    ├── /new → session.setActiveClaudeSessionId(null)（开启新 Claude 会话）
    ├── /sessions → ClaudeSessionRepository.findByUserIdOrderByUpdatedDesc(10) → 序号列表
    ├── /resume <序号|id> → 解析为 sessionId → setActiveClaudeSessionId + 切到 CLAUDE
    ├── /help → 系统命令清单
    └── /status → 当前模式 + 玩家状态

=== Claude Bridge 流（ClaudeBridgeMode，/mode claude 后的普通文本）===
ClaudeBridgeMode.handleText
    │
    ├── adapter == null？→ sender.sendText("Claude Bridge 未启用…")
    │
    ├── resumeSessionId = session.getActiveClaudeSessionId()（可空=新会话）
    ├── sender.startTyping(userId)
    └── executor.submit(() → adapter.run(userId, prompt, resumeSessionId, callback))
        ├── ProcessBuilder 启动 claude -p ... --output-format stream-json [--resume <id>]
        ├── init 事件 → callback.onSessionId
        │   ├── 新会话 → ClaudeSessionRepository.insert（title=prompt 前 40 字）
        │   │            + session.setActiveClaudeSessionId(id)
        │   └── 续传   → ClaudeSessionRepository.touchUpdatedAt(id)
        ├── token 文本 → callback.onToken（刷新 typing）
        ├── is_error/403 → callback.onError → 友好提示
        └── 结束 → callback.onComplete → splitMessage(2000) → 多条 sendText

=== 视频任务流（ReviewMode）===
ReviewMode.handleVideo（视频消息触发，抢占式）
    │
    └── 委托 TaskMessageHandler.tryHandleVideo
        ├── client.downloadVideoFromMessageItem(item) → byte[]
        ├── videoBuffer.put(userId, bytes, fileName)
        │   └── 大小超 50MB 或为空 → 回复"视频处理失败"
        └── sender.sendText(userId, "🎬 已收到视频...")

ReviewMode.handlePendingPrompt（60s 票据未过期且用户发送了非前缀文字）
    │
    └── 委托 TaskMessageHandler.tryHandleTaskText
        ├── videoBuffer.consume(userId) → VideoTicket
        ├── 立即回复 "🤖 任务已提交..."
        ├── TaskRequest 构造
        └── taskExecutor.submit(() → ...)
            ├── DashScopeUploader.uploadVideo → OSS url
            ├── DashScopeVideoProvider.callChat（/chat/completions + video_url 块）
            ├── SSE 解析 → callback.onComplete
            └── onComplete → splitMessage → 多条 sendText
```

## 模式切换持久化

```
用户发送 /mode chat
    │
    ▼
SystemCommandMode.handleMode
    │
    ├── session.setCurrentMode(CHAT)（标记 dirty）
    └── sessions.saveSession(session)
        │
        ▼
    PlayerRepository.update
        └── UPDATE player SET bot_mode = 'CHAT', updated_at = ? WHERE user_id = ?
            │
            ▼
        下次 SessionManager.getOrCreate(userId) 时从 DB 加载，恢复 currentMode
```

## 会话生命周期

```
首次消息
    │
    ▼
SessionManager.getOrCreate(userId)
    │
    ├── ConcurrentHashMap 缓存命中且未过期 → 直接返回
    ├── 缓存命中但已过期（30min）→ 移除缓存，从 SQLite 重新加载
    └── 缓存未命中 → loadFromDb(userId) 从 SQLite 加载
        ├── 存在 → 通过 PlayerRepository + FarmPlotRepository + InventoryRepository 组装 PlayerSession
        └── 不存在 → 创建新会话，PlayerRepository.insert() 写入 SQLite
    │
    ▼
命令处理过程中读写会话状态（纯内存操作）
    │
    ▼
命令成功后 scheduleFlush() 经 FlushGate 写入 SQLite（在 SessionManager 每用户锁内，仅 isDirty 时；默认同步立即刷，可配 debounce 合并 + 周期兜底）
    │
    ▼
会话过期检查（每次 getOrCreate 时）
    │
    ├── lastActivity + 30min < now → 从缓存移除
    └── 未过期 → 继续使用
```

## 命令注册流程

```
GameApplication.start()
    │
    ▼
new FarmGame(registry, rankRepo).registerCommands()
    │
    ├── registry.register(new UserInfoCommand())
    ├── registry.register(new ViewFarmCommand())
    ├── registry.register(new HelpCommand())
    ├── ...（共 22 个命令）
    │
    ├── registry.registerAlias("我的信息", "USER_INFO")
    ├── registry.registerAlias("签到", "CHECKIN")
    ├── ...（共 47 个别名）
    │
    ▼
CommandRegistry 内部 Map<String, Command>
    │
    ▼
运行时通过 registry.find(name) 查找
```

## 日志埋点位置

| 位置 | 级别 | 内容 |
|------|------|------|
| GameBot.onMessages | ERROR | userId, 路由异常（catch-all 兜底） |
| 各 BotMode（ChatMode/FarmMode/…） | ERROR | userId, 模式内异常（细粒度，如 LLM 错误/队列拒绝） |
| CommandParser.parse | DEBUG | rawText → parsedCommand |
| GameEngine.dispatch | INFO | userId, commandName, 耗时ms |
| Command.execute | DEBUG | session 状态变更 |
| SessionManager.getOrCreate | DEBUG | userId, 新建/复用/从 DB 加载 |
| RetrySender（sender） | ERROR | userId, 发送重试/最终失败 |
| 异常捕获 | ERROR | userId, commandName, 异常消息 |

## 线程安全模型

```
每用户锁 (SessionManager.lockFor(userId)，锁下沉后由 SessionManager 持有)

用户 A 的消息
    │
    ▼
synchronized(sessionManager.lockFor("A"))
    │
    ├── 获取会话（ConcurrentHashMap 缓存 → 或从 SQLite 加载）
    ├── session.touchActivity()
    ├── 执行命令（纯内存）
    └── scheduleFlush("A") 经 FlushGate 刷盘（默认同步立即刷，仅 dirty 时；异步 flush 复用同一把锁读一致快照）
    │
    ▼
锁外：render + sender.sendText（RetrySender 装饰，避免持有锁时阻塞网络 I/O）

用户 B 的消息 → 独立锁，并行处理
```
