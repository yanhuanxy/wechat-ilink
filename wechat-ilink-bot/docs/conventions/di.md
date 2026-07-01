# 依赖注入

## 原则

- 无 DI 框架、无 Spring、无 Lombok
- 构造器注入，手动编写构造器
- 所有字段 `private final`
- `GameApplication` 是唯一组合根

## 组合根：GameApplication

所有依赖在 `GameApplication.start()` 中组装：

```java
public void start() {
    // 1. 初始化数据库
    DatabaseManager dbManager = new DatabaseManager("data/farm_game.db");
    dbManager.initialize();

    // 2. 创建框架层组件（由内到外）
    SessionManager sessions = new SessionManager(dbManager);
    CommandRegistry registry = new CommandRegistry();
    ResponseRenderer renderer = new ResponseRenderer();
    ActionRankRepository rankRepo = new ActionRankRepository(dbManager);

    // 3. 注册游戏（实现层）
    FarmGame farmGame = new FarmGame(registry, rankRepo);
    farmGame.registerCommands();

    CommandParser parser = new CommandParser(registry);
    GameEngine engine = new GameEngine(parser, sessions, registry);

    // 4. 初始化 LLM（可选）
    ModelsConfig modelsConfig = ModelsConfig.load("data/models-config.json");
    LlmConfig llmConfig = modelsConfig.resolveChatLlmConfig();
    LlmProvider llmProvider = createProvider(llmConfig);
    ChatHistoryManager chatHistory = new ChatHistoryManager(llmConfig.getMaxHistory());

    // 5. 组装 BotInstance（内部 new GameBot 规范 14 参构造器 + 扫码登录重试；多账号循环创建）
    //    GameBot 不再在组合根直接 new —— 经 BotInstance.create 注入 reliability/mcp/sessions/llm 等；
    //    完整启动序列见 docs/design/game-application.md
    for (BotConfig cfg : botConfigs) {
        BotInstance instance = BotInstance.create(cfg, dbManager, sessions, llmProvider,
                chatHistory, llmQueue, streamingEnabled, typingIntervalMs, shareProvider,
                taskHandler, taskConfig, mcpClient, mcpToolRegistry, reliabilityConfig);
        instance.start();
    }
}
```

## 正确示例

```java
public class GameEngine {
    private final CommandParser commandParser;
    private final SessionManager sessionManager;
    private final CommandRegistry registry;

    public GameEngine(CommandParser commandParser, SessionManager sessionManager, CommandRegistry registry) {
        this.commandParser = commandParser;
        this.sessionManager = sessionManager;
        this.registry = registry;
    }
}
```

## 错误示例

```java
// 错误 1：字段非 final
public class GameEngine {
    private CommandParser parser; // 应为 private final
}

// 错误 2：无构造器注入
public class GameEngine {
    private CommandParser parser = new CommandParser(); // 硬编码依赖
}

// 错误 3：使用 setter 注入
public class GameEngine {
    private CommandParser parser;
    public void setParser(CommandParser parser) { ... } // 禁止
}

// 错误 4：使用单例/静态
public class GameEngine {
    private static final SessionManager INSTANCE = new SessionManager(); // 禁止
}
```

## 共享实例

`SessionManager` 和 `CommandRegistry` 是跨组件共享的实例，由组合根创建一次，注入到需要的位置。不使用单例模式。
