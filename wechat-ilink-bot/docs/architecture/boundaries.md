# 架构边界

## 层级边界规则

### Application Layer

**允许**：
- 创建和组装 Framework 层所有组件
- 持有 `ILinkClient` 实例（仅 `GameBot`）
- 调用 Framework 层的公共 API
- 管理 SDK 登录和长轮询生命周期

**禁止**：
- 实现游戏逻辑
- 直接操作 `ConcurrentHashMap` 存储会话
- 拼接用户响应文本（使用 ResponseRenderer）

### Framework Layer

**允许**：
- 定义游戏引擎的通用接口和调度逻辑
- 管理命令注册表和会话存储
- 解析文本命令、格式化响应
- 被多个游戏实现共享

**禁止**：
- import `ILinkClient` 或任何 SDK 类
- 包含特定游戏的业务逻辑（如作物生长规则）
- 直接发送消息给用户（返回 CommandResult 给上层）

### Implementation Layer

**允许**：
- 实现 `Command` 接口
- 定义游戏专属的领域模型
- 访问 `PlayerSession` 中的游戏状态
- 注册命令到 `CommandRegistry`

**禁止**：
- import `ILinkClient` 或 SDK 类
- import Application 层的类
- import 其他游戏的包（`farm` 不能 import `pet`）
- 直接操作 `SessionManager` 的内部存储

### Persistence Layer

**允许**：
- 管理 SQLite 数据库连接和表结构（`DatabaseManager`）
- 通过 Repository 类提供 CRUD 操作
- 被 SessionManager 和命令处理器（排行榜）调用

**禁止**：
- 包含游戏业务逻辑
- import Application 层或 SDK 类

### LLM Layer

**允许**：
- 提供抽象的 LLM 对话接口（`LlmProvider`）
- 管理对话历史滑动窗口（`ChatHistoryManager`）
- 加载 LLM 配置并自动生成模板（`LlmConfig`）

**禁止**：
- 包含游戏业务逻辑
- 直接被 Implementation 层调用（通过 GameBot 间接访问）
- import Application 层或 SDK 类

### Mode Layer（Framework 子层）

**允许**：
- 定义 `BotMode` 接口与具体模式（ChatMode/FarmMode/ReviewMode/SystemCommandMode/ClaudeBridgeMode）
- 通过 `ModeRouter` 按优先级路由消息：视频 → `#` → `/` → pending ticket → 当前模式
- 通过 `ModeSender` 抽象发送消息（保持 SDK 隔离）
- 持有 `ModeContext`（engine/renderer/llmProvider/chatHistory/llmQueue/sessions/taskHandler/claudeSessionRepo 等依赖的不可变载体）
- `claude/` 子包封装 `claude` 子进程交互（`ClaudeCodeAdapter`/`ClaudeAdapterCallback`/`ClaudeSession`），通过 `ProcessBuilder` 启动子进程，不 import SDK

**禁止**：
- import `ILinkClient` 或任何 SDK 类（统一通过 `ModeSender` 回调到 GameBot）
- 直接操作 `ConcurrentHashMap` 存储会话
- 跨模式共享可变状态（每个 Mode 无状态，状态存于 `PlayerSession`；Claude 活跃会话存于 `PlayerSession` 的 transient 字段）

## 公共 API 边界

| 类 | 对外暴露的 API | 使用方 |
|----|-------------|--------|
| `GameApplication` | 构造器 + `start()` | main 方法 |
| `GameBot` | 构造器 + `setClient(ILinkClient)` + `ModeSender` 实现 | GameApplication |
| `ModeRouter` | `route(WeixinMessage): ModeOutcome` | GameBot |
| `BotMode` | `type()`, `handleText(ModeContext, PlayerSession, String): ModeOutcome` | ModeRouter |
| `ModeContext` | 构造器 + getter（依赖载体） | BotMode |
| `ModeSender` | `sendText`/`sendTextWithTyping`/`sendImage`/`sendFile`/`sendVideo`/`startTyping`/`stopTyping` | BotMode（实由 `RetrySender` 装饰后注入） |
| `GameEngine` | `dispatch(userId, rawText): CommandResult` | FarmMode |
| `Command` | `name()`, `description()`, `execute(session, args)` | 命令处理器 |
| `CommandRegistry` | `register(command)`, `find(name)`, `registerAlias(alias, name)` | 游戏入口类 |
| `SessionManager` | `getOrCreate(userId)`, `remove(userId)`, `saveSession(session)`, `scheduleFlush(userId)`, `lockFor(userId)`, `withLock(userId, Runnable)`, `shutdown()` | GameEngine, ModeRouter |
| `RetrySender`（mode/） | `ModeSender` 装饰器（内容发送指数退避重试） | GameBot 注入 ModeContext.sender |
| `RateLimiter`（mode/） | `tryAcquire(userId): boolean` | ModeRouter.route 入口 |
| `FlushGate`（session/） | `scheduleFlush(userId)`, `flushAllNow()`, `shutdown()` | SessionManager |
| `McpHealthMonitor`（mcp/） | `start()`, `shutdown()` | GameApplication |
| `ReliabilityConfig`（config/） | `load(path)` + 8 旋钮 getter | GameApplication → BotInstance/GameBot/SessionManager/McpHealthMonitor |
| `PlayerSession` | getter/setter 方法（含 `currentMode`） | 命令处理器, BotMode |
| `DatabaseManager` | 构造器 + `initialize()`, `getConnection()`, `close()` | Repository |
| `ResponseRenderer` | `render(CommandResult): String` | GameBot |
| `LlmProvider` | `chat(List<ChatMessage>): String` | GameBot |
| `ChatHistoryManager` | `addMessage(userId, role, content)`, `getHistory(userId)` | GameBot |
| `ActionRankRepository` | `incrementScore(actionType, userId, delta)`, `getTopScores(actionType, limit)` | 排行榜命令 |

## 注入规则

### 正确示例

```java
// GameApplication 是唯一组合根
public class GameApplication {
    public void start() {
        DatabaseManager dbManager = new DatabaseManager("data/farm_game.db");
        dbManager.initialize();

        SessionManager sessions = new SessionManager(dbManager);
        CommandRegistry registry = new CommandRegistry();
        ResponseRenderer renderer = new ResponseRenderer();
        ActionRankRepository rankRepo = new ActionRankRepository(dbManager);

        FarmGame farmGame = new FarmGame(registry, rankRepo);
        farmGame.registerCommands();

        CommandParser parser = new CommandParser(registry);
        GameEngine engine = new GameEngine(parser, sessions, registry);

        ModelsConfig modelsConfig = ModelsConfig.load("data/models-config.json");
        LlmConfig llmConfig = modelsConfig.resolveChatLlmConfig();
        LlmProvider llmProvider = createProvider(llmConfig);
        ChatHistoryManager chatHistory = new ChatHistoryManager(llmConfig.getMaxHistory());

        // GameBot 不再在组合根直接 new —— 经 BotInstance.create 内部组装（规范 14 参构造器，
        // 注入 reliability/mcp/sessions/llm 等）。client 构建需要 bot 作监听器，故 BotInstance 内部
        // 用 setClient 延迟注入 ILinkClient，再扫码登录。多账号循环创建。完整序列见 game-application.md
        for (BotConfig cfg : botConfigs) {
            BotInstance instance = BotInstance.create(cfg, dbManager, sessions, llmProvider,
                    chatHistory, llmQueue, streamingEnabled, typingIntervalMs, shareProvider,
                    taskHandler, taskConfig, mcpClient, mcpToolRegistry, reliabilityConfig);
            instance.start();
        }
    }
}
```

### 错误示例

```java
// 错误 — 命令处理器直接使用 ILinkClient
public class PlantCommand implements Command {
    private ILinkClient client; // 禁止！
}

// 错误 — 框架层 import 实现层
import com.github.wechat.ilink.bot.farm.model.Crop; // GameEngine 中禁止！

// 错误 — 游戏之间互相引用
import com.github.wechat.ilink.bot.pet.handler.*; // farm 包中禁止！
```

## 游戏隔离

每个游戏作为独立子包：
- `farm/` — 农场游戏
- `pet/` — 未来：宠物游戏
- `mud/` — 未来：MUD 冒险

游戏之间通过 `PlayerSession` 中的游戏状态字段隔离，不直接引用对方的类。
