# 架构概述

> 项目定位见根 [README.md](../../README.md)，后续路线见 [ROADMAP.md](../ROADMAP.md)。

## 三层架构

```
┌──────────────────────────────────────────────────────────┐
│                     Application Layer                     │
│  GameApplication · GameBot · BotInstance                  │
│  （组合根 + SDK 消息桥 + 多实例/登录重试；路由委托 ModeRouter）│
├──────────────────────────────────────────────────────────┤
│                      Framework Layer                      │
│  mode/（ModeRouter + Chat/Farm/ClaudeBridge/Review/System/ │
│        Autogame + RetrySender/RateLimiter + claude/）     │
│  engine/ · command/ · session/（+FlushGate）               │
│  persistence/ · llm/ · task/ · mcp/ · config/             │
│  （路由/命令/会话/渲染/持久化/LLM/任务/MCP/可靠性）          │
├──────────────────────────────────────────────────────────┤
│                   Implementation Layer                    │
│  farm/  ·  (future: 更多游戏 / BotMode 实现)              │
│  （具体游戏：命令处理器 + 领域模型）                        │
└──────────────────────────────────────────────────────────┘
```

**依赖方向**：Application → Framework → Implementation，严格向下。`mode/` 包零 SDK import。

> 名词约定：本项目的「**运行时 harness**」指 `GameBot`(SDK 收发边界) + `ModeRouter`(路由) + 各 `BotMode`(能力执行体) + 可靠性四件套这条编排主干；hook 子系统（**已实现 H1–H4**，见 [hooks.md](../design/hooks.md)）即围绕它的横切拦截层。

## 包结构

```
com.github.wechat.ilink.bot/
├── GameApplication.java          # 组合根：加载配置、组装 BotInstance、长轮询、优雅关闭
├── GameBot.java                  # SDK 消息桥 + ModeSender/MediaDownloader 实现，路由委托 ModeRouter
├── BotInstance.java              # 单个微信账号实例（多实例 + 扫码登录重试 + 欢迎消息）
├── BotConfig.java                # 账号配置 POJO
│
├── mode/                         # 框架层 - 多模式路由
│   ├── BotMode.java              # 模式接口（type + handleText）
│   ├── BotModeType.java          # 模式枚举（CHAT/CLAUDE/FARM/REVIEW/AUTOGAME）
│   ├── ModeRouter.java           # 按优先级路由：视频 → # → / → ! → ticket → 当前模式（入口限流）
│   ├── ModeContext.java          # 不可变依赖载体（15 字段：sender/downloader/engine/.../mcpClient/mcpToolRegistry/hooks，经 Builder 构造）
│   ├── ModeSender.java           # Mode → GameBot 发送回调（sendText/Image/File/Video + typing）
│   ├── MediaDownloader.java      # 入向媒体下载 seam（对称 ModeSender，GameBot 实现）
│   ├── ModeOutcome.java          # 路由结果（HANDLED/NOT_MATCHED/SKIP/ERROR）
│   ├── RetrySender.java          # ModeSender 装饰器（发送指数退避重试，可靠性）
│   ├── RateLimiter.java          # per-user 固定窗口限流（可靠性）
│   ├── ChatMode.java             # LLM 聊天模式（默认）
│   ├── FarmMode.java             # 农场游戏模式（# 前缀）
│   ├── ReviewMode.java           # 视频点评模式（抢占式）
│   ├── SystemCommandMode.java    # 系统命令模式（/ 前缀）
│   ├── ClaudeBridgeMode.java     # Claude Bridge 模式（claude 子进程 + 会话延续 + 双向文件）
│   ├── AutogameMode.java         # Autogame 模式（! 前缀 → MCP 调 autogame-xcx）
│   ├── claude/                   # Claude Bridge 适配层
│   │   ├── ClaudeCodeAdapter.java     # session-aware claude 子进程（--resume 续传）
│   │   ├── ClaudeAdapterCallback.java # onSessionId/onToken/onComplete/onError
│   │   ├── ClaudeSession.java         # 会话元数据 POJO
│   │   ├── BridgeFileBuffer.java      # per-user 60s 入向文件票据缓冲
│   │   └── BridgeWorkspace.java       # <cwd>/<userId>/{input,output} 工作目录
│   └── hook/                     # 生命周期 hook 子系统（Phase H1–H4）
│       ├── HookEvent.java             # 生命周期事件枚举（ON_MESSAGE_RECEIVED/ON_TEXT_RECEIVED/PRE_SEND/...）
│       ├── HookContext.java           # 不可变事件负载（Builder）
│       ├── HookVerdict.java           # 控制流裁决（continue/block/shortCircuit）
│       ├── BotHook.java               # hook 接口（event + handle）
│       ├── HookRegistry.java          # 按 event 分组注册表 + fire
│       ├── InboundAuditHook.java      # ON_TEXT_RECEIVED → 入向审计
│       ├── OutboundAuditHook.java     # PRE_SEND → 出向审计
│       └── RateLimitHook.java         # ON_MESSAGE_RECEIVED → 入口限流
│
├── engine/                       # 框架层 - 核心引擎
│   ├── GameEngine.java           # 命令调度（per-user 锁来自 SessionManager）
│   ├── CommandParser.java        # 文本解析
│   └── ResponseRenderer.java     # 文本格式化
│
├── command/                      # 框架层 - 命令系统
│   ├── Command.java              # 命令接口
│   ├── CommandResult.java        # 执行结果
│   ├── CommandRegistry.java      # 命令注册表
│   ├── ParsedCommand.java        # 解析结果
│   └── QrCodeProvider.java       # 登录二维码内容提供（ZXing）
│
├── session/                      # 框架层 - 会话管理
│   ├── SessionManager.java       # 会话生命周期 + lockFor/withLock + scheduleFlush
│   ├── PlayerSession.java        # 玩家状态（含 currentMode / activeClaudeSessionId）
│   └── FlushGate.java            # 持久化 debounce 合并 + 周期兜底（可靠性）
│
├── persistence/                  # 框架层 - 持久化（SQLite）
│   ├── DatabaseManager.java      # 连接/建表/WAL/schema 迁移（bot_mode 列、bot_session 表）
│   ├── PlayerRepository.java     # 玩家 CRUD（含 bot_mode）
│   ├── FarmPlotRepository.java   # 地块 CRUD
│   ├── InventoryRepository.java  # 背包 CRUD
│   ├── ActionRankRepository.java # 排行榜 CRUD
│   ├── ClaudeSessionRepository.java # Claude 会话元数据 CRUD（claude_sessions 表）
│   ├── BotSessionRepository.java # bot 登录会话 CRUD（bot_session 表，免扫码恢复）
│   └── BotSessionRecord.java     # bot 会话记录 POJO（纯字符串，零 SDK 依赖）
│
├── llm/                          # 框架层 - LLM 对话
│   ├── ModelsConfig.java         # 统一模型/Provider 注册表（providers 共享 + chat/review/bridge 各引用 provider+model）
│   ├── LlmConfig.java            # Chat 值对象（由 ModelsConfig.resolveChatLlmConfig 解析得到）
│   ├── LlmProvider.java          # 抽象 Provider 接口（chat / chatStream）
│   ├── OpenAiProvider.java       # OpenAI 兼容 HTTP 实现
│   ├── LlmRequestQueue.java      # 有界线程池 + per-user 并发限制（背压）
│   ├── ChatMessage.java          # 对话消息模型
│   ├── ChatHistoryManager.java   # 滑动窗口历史
│   ├── SseParser.java            # SSE 流解析
│   └── StreamCallback.java       # 流式回调（onToken/onComplete/onError）
│
├── task/                         # 框架层 - 任务子系统（视频 Claude Code / DashScope）
│   ├── TaskProvider.java         # 抽象 Provider 接口
│   ├── TaskRequest.java          # 任务请求（含视频字节）
│   ├── TaskConfig.java           # 任务配置（含 claudeBridge*/工具策略）
│   ├── TaskMessageHandler.java   # 消息路由 + 视频缓冲 + 任务派发
│   ├── ClaudeCodeProvider.java   # Claude Code CLI 实现（ProcessBuilder + stream-json）
│   ├── DashScopeVideoProvider.java # DashScope 视频模型实现
│   ├── DashScopeUploader.java    # DashScope 文件上传
│   ├── VideoTaskBuffer.java      # 60s 窗口视频缓冲
│   ├── WorkspaceManager.java     # per-user / per-task 工作目录
│   └── SkillInstaller.java       # Claude Code skill 安装
│
├── mcp/                          # 框架层 - MCP 客户端（autogame-xcx 远程服务）
│   ├── McpClient.java            # JSON-RPC over HTTP+SSE（reconnect 自愈、pending 清理）
│   ├── McpToolRegistry.java      # tool 列表注册（可刷新）
│   ├── McpTool.java              # tool 元数据 POJO
│   ├── McpToolResult.java        # tool 调用结果
│   ├── McpHealthMonitor.java     # 周期健康探测 + 断线重连 + tool 刷新（可靠性）
│   └── AutogameConfig.java       # autogame MCP 配置（enabled/mcpUrl=:8765）
│
├── config/                       # 框架层 - 可靠性 / hook 配置
│   ├── ReliabilityConfig.java    # 8 旋钮（重试/限流/刷盘/MCP 健康）
│   └── HookConfig.java           # hook 开关（audit/rateLimit，默认全启用）
│
├── util/                         # 框架层 - 工具类
│   ├── TextFormatter.java        # 文本格式化（截断、对齐、分隔线）
│   ├── Clock.java                # 时间工具（可注入，便于测试）
│   ├── QrCodeGenerator.java      # 登录二维码渲染（ZXing）
│   └── MessageAuditLog.java      # 收发审计（MDC userId → 按用户按日期落 logs/io/）
│
└── farm/                         # 实现层 - 农场游戏
    ├── FarmGame.java             # 游戏入口（注册命令）
    ├── handler/                  # 22 个命令处理器
    └── model/                    # 领域模型（Crop/CropRegistry/CropStage/FarmPlot/Inventory/Weather）
```

## 依赖方向

```
GameApplication → BotInstance → GameBot → ModeRouter → { Chat, Farm, System, Review, ClaudeBridge, Autogame }
BotMode → ModeContext（15 字段，含 sender/downloader/engine/renderer/llm/chatHistory/llmQueue/sessions/
                        taskHandler/claudeSessionRepo/streaming/typing/mcpClient/mcpToolRegistry/hooks）
ModeContext.sender = RetrySender(GameBot)     # 发送重试装饰
ModeContext.downloader = GameBot(MediaDownloader)   # 入向媒体下载
ClaudeBridgeMode → ClaudeCodeAdapter（claude 子进程）+ ClaudeSessionRepository + BridgeFileBuffer/Workspace
AutogameMode → McpClient（JSON-RPC over SSE）→ autogame-xcx MCP server(:8765)
McpHealthMonitor → McpClient.reconnect / McpToolRegistry.refresh（daemon 线程，不阻塞消息线程）
GameEngine.dispatch 用 SessionManager.lockFor(userId)；落盘经 scheduleFlush → FlushGate
GameApplication → SessionManager（全构造器，flushDelay/interval）→ FlushGate
SessionManager → { Player/FarmPlot/Inventory/ActionRank/ClaudeSession Repository } → DatabaseManager
FarmGame → CommandRegistry, ActionRankRepository
GameBot / TaskMessageHandler → setClient(ILinkClient)（客户端构建需其作监听器）
```

**规则**：
- Application 层可调用 Framework 层
- Framework 层可调用自身子包
- Implementation 层可调用 Framework 层接口（Command、PlayerSession、ActionRankRepository）
- Implementation 层不可调用 Application 层；不同游戏之间不可互相引用
- `mode/` 包零 SDK import，统一经 `ModeSender` / `MediaDownloader` 回调 `GameBot`

## 各层职责

### Application Layer
- `GameApplication`：加载配置（llm/task/reliability/autogame/bots）、组装 `BotInstance`、启动 SDK 长轮询、优雅关闭（关 monitor/mcpClient/flushAll）
- `BotInstance`：单个微信账号实例——扫码登录（重试）、持有 `GameBot`、登录成功后发欢迎菜单；支持多实例（`bots.json`）
- `GameBot`：实现 `OnMessageListener` + `ModeSender` + `MediaDownloader`，每条消息委托 `ModeRouter`，自身只做 SDK 收发

### Framework Layer
- `ModeRouter`：按优先级路由（视频 → `#` → `/` → `!` → 60s ticket → 当前模式），入口 `RateLimiter` gate
- `BotMode`：共 **6 个实现** = 5 个用户模式（对应 `BotModeType`：CHAT/CLAUDE/FARM/REVIEW/AUTOGAME）+ `SystemCommandMode`（处理 `/`，无独立 `BotModeType`、不可经 `/mode` 切换）。每个实现 `type()`/`handleText()`，无状态，状态存于 `PlayerSession`
- `ClaudeBridgeMode`：普通文本 → `claude` 子进程异步执行，首条建会话、后续 `--resume`，元数据落 `claude_sessions`；支持双向文件回传
- `AutogameMode`：`!` 命令 → `McpClient.callTool` 调 autogame-xcx MCP server
- `ModeContext`：不可变依赖载体（15 字段，经 Builder 构造），所有 Mode 经它访问依赖
- `RetrySender` / `RateLimiter` / `FlushGate` / `McpHealthMonitor`：可靠性四件套（见 [reliability.md](../design/reliability.md)）
- Hook 子系统（**已实现 H1–H4**，见 [hooks.md](../design/hooks.md)）：生命周期拦截层，把审计/限流/错误兜底/模式切换等横切关注点从 `ModeRouter`/`GameBot` 解耦为可插拔 hook，对标 Claude Code 的 PreToolUse/PostToolUse/Stop
- `GameEngine` / `CommandParser` / `ResponseRenderer` / `CommandRegistry`：命令调度/解析/渲染/注册
- `SessionManager` / `PlayerSession` / `FlushGate`：会话生命周期 + 持久化合并
- `DatabaseManager` + 5 个 Repository：SQLite CRUD（WAL）
- `LlmProvider` / `OpenAiProvider` / `ChatHistoryManager` / `LlmRequestQueue`：LLM 对话 + 背压
- `TaskProvider` / `ClaudeCodeProvider` / `DashScopeVideoProvider`：视频任务
- `McpClient` / `McpToolRegistry` / `McpHealthMonitor`：MCP 客户端 + 自愈

### Implementation Layer
- 每个 `XxxCommand` 实现 Command 接口（22 个农场命令处理器）
- 游戏领域模型（Crop、FarmPlot 等）属于特定游戏
- 游戏入口类（`FarmGame`）向 `CommandRegistry` 注册该游戏命令

## 依赖说明

- **wechat-ilink-sdk-java**（地基）：微信 PC 客户端封装——扫码登录、长轮询、收发文本/图/文件/视频、CDN 上传/下载 + AES 解密
- **SQLite JDBC**：嵌入式数据库，持久化玩家/地块/背包/排行/Claude 会话/模式/bot 登录会话
- **SLF4J + Logback**：结构化日志落地——系统日志（按日期滚动）、收发审计（MDC `userId` SiftingAppender，按用户按日期）、Claude 桥接生命周期日志（详见 [logging.md](../design/logging.md)）
- **okhttp + okhttp-eventsource**：MCP 客户端的 HTTP POST + SSE 长连接
- **ZXing**：登录二维码生成与渲染
- 无需外部数据库服务，数据库文件随应用存储；通过 `DatabaseManager` 统一管理连接生命周期
