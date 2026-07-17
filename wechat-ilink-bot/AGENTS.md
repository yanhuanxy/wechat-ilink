# AGENTS.md

> **本文件是本项目的唯一契约真相源（single source of truth）。** Claude Code 经 [CLAUDE.md](CLAUDE.md) 的 `@AGENTS.md` 导入本文；其它 AI 工具直接读本文。项目门面见 [README.md](README.md)。
> 维护本文请遵守 [.claude/rules/doc-maintenance.md](.claude/rules/doc-maintenance.md)：技术基线 / 包结构 / 硬性规则**只在本文有权威副本**，其余文档链接本文而非重述。

## 项目概述

**wechat-ilink-bot** —— 基于 [`wechat-ilink-sdk`](../wechat-ilink-sdk-java) (3.0.0) 构建的**多模式微信机器人**。框架负责多模式路由（`ModeRouter`）、命令解析、用户会话管理、消息分发和响应格式化；单个模式只需实现 `BotMode`。

`ModeRouter` 按优先级路由（入口 `RateLimiter` 限流）：

| 触发 | 模式 | BotModeType | 做什么 |
|------|------|-------------|--------|
| 普通文本（默认） | Chat | `CHAT` | LLM 对话（流式/同步）；未配 LLM 原样回显 |
| `#` 前缀 | Farm | `FARM` | 帮帮农场（27 命令，经 GameEngine） |
| `/mode claude` 后文本 / CLAUDE 媒体 | Claude Bridge | `CLAUDE` | `claude` 子进程（`--resume`）+ 双向文件回传 |
| 上传视频（抢占式） | Review | `REVIEW` | 视频评测（DashScope 视频模型） |
| `!` 前缀 | Autogame | `AUTOGAME` | MCP 调 [wechat-link-autogame-xcx](../wechat-link-autogame-xcx) |
| `/` 前缀 | 系统命令 | — | `/mode` `/new` `/sessions` `/resume` `/help` `/status` |

`CHAT/CLAUDE/AUTOGAME` 可经 `/mode` 切换并持久化（`player.bot_mode`）；`FARM/REVIEW` 抢占式触发，无需切换。

## 技术基线（不允许擅自升级）

| 技术 | 版本 | 约束 |
|------|------|------|
| JDK | 17 | 可使用 Java 17 语法（var、record、sealed、text block、switch 表达式/模式匹配） |
| Maven | 3.6.3 | 由 enforcer 强制 |
| SDK | 3.0.0 | `io.github.lith0924:wechat-ilink-sdk` |
| 数据库 | SQLite 3.45.x + sqlite-jdbc 3.45.x | 嵌入式，零配置，ACID，WAL 模式 |
| JSON | Jackson 2.x | 复杂数据类型序列化辅助（SDK 已自带） |
| HTTP/SSE | okhttp + okhttp-eventsource | MCP 客户端（JSON-RPC over HTTP+SSE） |
| 二维码 | ZXing | 登录二维码 |
| 日志 | SLF4J + Logback | 统一结构化日志（SDK 已自带） |
| 测试 | JUnit Jupiter 5.10.x + Mockito 5.x | 覆盖率 ≥ 80%（CI 闸 ≥ 75%） |

**禁止**：Spring、DI 框架、Lombok（决策记录见 [docs/adr/0001](docs/adr/0001-no-spring-di-lombok.md)）。

## 架构（三层，依赖严格向下）

```
Application Layer（应用层）
  └── GameApplication    — 组合根，加载配置/组装多实例/生命周期管理
  └── BotInstance        — 单账号实例（多实例 + 扫码登录重试）
  └── GameBot            — SDK 消息桥接 + ModeSender/MediaDownloader 实现，路由委托 ModeRouter

Framework Layer（框架层）
  └── mode/              — ModeRouter, BotMode/ModeContext/ModeSender/MediaDownloader + Chat/Farm/ClaudeBridge/Review/System/Autogame 模式 + RetrySender/RateLimiter + hook/、claude/ 子包
  └── engine/            — GameEngine, CommandParser, ResponseRenderer
  └── command/           — Command 接口, CommandRegistry, CommandResult, ParsedCommand, QrCodeProvider
  └── session/           — SessionManager, PlayerSession, FlushGate
  └── persistence/       — DatabaseManager, Player/FarmPlot/Inventory/ActionRank/ClaudeSession Repository
  └── llm/               — LlmConfig, LlmProvider, OpenAiProvider, LlmRequestQueue, ChatHistoryManager, ChatMessage, SseParser, StreamCallback
  └── task/              — TaskProvider/Request, TaskMessageHandler, DashScopeUploader, DashScopeVideoProvider, VideoTaskBuffer, SkillInstaller
  └── mcp/               — McpClient, McpToolRegistry, McpHealthMonitor, McpTool, McpToolResult, AutogameConfig
  └── config/            — ReliabilityConfig（可靠性 8 旋钮）、HookConfig（hook 开关）
  └── util/              — TextFormatter, Clock, QrCodeGenerator

Implementation Layer（实现层）
  └── farm/              — 帮帮农场（27 命令处理器 + 领域模型 + FarmGame 入口）
```

**依赖方向**：Application → Framework → Implementation，严格向下。`mode/` 包零 SDK import（统一经 `ModeSender`/`MediaDownloader` 回调 `GameBot`）。

## 硬性规则

1. **依赖方向**：engine → command → session → persistence。游戏实现可调用框架层接口（Command、PlayerSession、ActionRankRepository），不可反向引用。不同游戏之间不可互相引用。
2. **无 DI 框架**：构造器注入。`GameApplication` 是唯一组合根，所有依赖在此组装。
3. **禁止 `System.out/printStackTrace`**，统一 SLF4J 结构化日志。
4. **单文件 <= 400 行**，单方法 <= 60 行。
5. **新功能需 JUnit 5 测试**，覆盖率 >= 80%。
6. **敏感数据**（botToken、contextToken）不得出现在日志中。
7. **所有 SDK 交互通过 `GameBot`**，应用层和框架层禁止直接 import `ILinkClient`。`GameBot` 通过 `setClient()` 接收 `ILinkClient`（因客户端构建需要 GameBot 作为监听器）。
8. **命令处理 <= 2 秒返回**（微信交互约束；长任务如 `!run`/Claude Bridge 异步执行）。
9. **文档同步**：代码变更后，按 [.claude/rules/doc-maintenance.md](.claude/rules/doc-maintenance.md) 中的映射表检查并更新相关文档；重大架构/选型决策（含 NO-GO）记入 [docs/adr/](docs/adr/README.md)。

## 场景锚点（你要做什么 → 先看这个）

| 你要做什么 | 先看这个 |
|-----------|---------|
| 项目门面 / 定位 | README.md |
| 后续路线 / 项目走向 | docs/ROADMAP.md |
| 理解多模式路由 | docs/design/mode-router.md |
| 理解整体架构（分层/组件全景） | docs/architecture/overview.md |
| 理解消息流 | docs/architecture/data-flow.md |
| 理解架构边界 | docs/architecture/boundaries.md |
| 查历史重大决策（为什么禁 Spring / 为什么 NO-GO） | docs/adr/README.md |
| MCP / autogame 模式 | docs/design/mcp-autogame.md |
| Claude Bridge 模式 | docs/design/claude-bridge.md |
| 视频任务子系统（Review） | docs/design/task-system.md |
| 可靠性（重试/限流/自愈/刷盘） | docs/design/reliability.md |
| hooks 子系统（生命周期拦截，已实现） | docs/design/hooks.md |
| 添加新命令（农场） | docs/conventions/command-pattern.md → docs/skills/add-command.md |
| 添加新游戏 | docs/design/game-application.md → docs/skills/add-game.md |
| GameBot / GameEngine / 渲染 / 解析 设计 | docs/design/{game-bot,game-engine,response-renderer,command-parser}.md |
| 查看命令规范 | docs/reference/command-spec.md |
| 查看错误码 | docs/reference/game-error-codes.md |
| 技术债清单 | docs/reference/tech-debt.md |
| 修 bug / 做新功能的流程 | docs/workflows/{bug-fix,new-feature}.md |
| 了解编码约定 | docs/conventions/README.md |
| 了解当前任务 | docs/plans/current-sprint.md |
| 调试命令处理 | docs/skills/debug-command.md |
| 设计游戏模型 | docs/design/farm-game.md |
| 农场社交玩法（偷菜/补偿） | docs/design/farm-social.md |
| 会话管理 | docs/conventions/session-management.md |
| 构建并冒烟验证 | `.claude/skills/run-bot/SKILL.md`（或 run.bat / run.sh） |
| 代码变更后同步文档 | .claude/rules/doc-maintenance.md |

## 编码约定

- Java 17 语法（var/record/sealed/text block 可用）
- 所有字段 `private final`，通过构造器注入
- 提交消息前缀：`feat:` / `fix:` / `refactor:` / `test:` / `docs:`
- 测试命名：`methodName_scenario_expectedBehavior`

## 常用命令

```powershell
mvn clean package        # 构建 + 全量测试 + JaCoCo 覆盖率（CI 闸 75%）
mvn test -Dtest=XxxTest  # 单测某类
run.bat                  # Windows 一键构建+运行（run.sh 为 *nix 版）；冒烟验证见 .claude/skills/run-bot
```

> 行为指南（Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution）由根级 [../CLAUDE.md](../CLAUDE.md) 统一定义，此处不重复。
