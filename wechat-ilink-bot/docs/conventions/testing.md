# 测试规范

## 框架

- JUnit Jupiter 5.10.x
- Mockito 5.x（mock SDK 依赖）
- Maven Surefire 插件
- 覆盖率目标 >= 80%

## 测试类型

### 单元测试（主要）

测试单个命令处理器的逻辑：

```java
class FarmCommandTest {

    private PlantAllCommand command;
    private PlayerSession session;

    @BeforeEach
    void setUp() {
        command = new PlantAllCommand();
        session = new PlayerSession("user123");
    }

    @Test
    void execute_validCrop_plantsSuccessfully() {
        String[] args = {"小麦"};

        CommandResult result = command.execute(session, args);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_emptyArgs_returnsError() {
        String[] args = {};

        CommandResult result = command.execute(session, args);

        assertFalse(result.isSuccess());
    }
}
```

### 集成测试

测试 GameEngine 完整调度流程：

```java
class GameEngineTest {

    private GameEngine engine;
    private SessionManager sessions;

    @BeforeEach
    void setUp() {
        DatabaseManager dbManager = new DatabaseManager(tempDbPath);
        dbManager.initialize();
        SessionManager sessions = new SessionManager(dbManager);
        CommandRegistry registry = new CommandRegistry();
        CommandParser parser = new CommandParser(registry);

        engine = new GameEngine(parser, sessions, registry);

        ActionRankRepository rankRepo = new ActionRankRepository(dbManager);
        new FarmGame(registry, rankRepo).registerCommands();
    }

    @Test
    void dispatch_plantCommand_returnsSuccess() {
        CommandResult result = engine.dispatch("user123", "种植 小麦");

        assertTrue(result.isSuccess());
    }
}
```

## 命名约定

```
methodName_scenario_expectedBehavior

例：
execute_validCrop_plantsSuccessfully
execute_emptyArgs_returnsError
parse_unknownCommand_returnsUnknown
dispatch_concurrentUsers_noRaceCondition
```

## 测试覆盖优先级

1. 正常流程（happy path）
2. 非法参数（空、null、越界）
3. 无效游戏状态（空地块收获、重复种植）
4. 边界条件（满背包、0 金币、地块编号上限）

## SDK 隔离

- 游戏逻辑测试**不依赖 SDK**
- 命令处理器测试不需要 mock `ILinkClient`（因为处理器不接触 SDK）
- `GameBot` 测试单独编写，mock `ILinkClient`
- `GameEngine` 测试不需要 SDK，使用真实的命令处理器

## 断言要求

每个测试至少一个断言：
```java
// 正确
assertTrue(result.isSuccess());

// 禁止 — 无断言的测试
@Test
void someTest() {
    command.execute(session, args);
    // 没有断言！
}
```
