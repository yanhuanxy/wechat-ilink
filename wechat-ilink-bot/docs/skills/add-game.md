# Skill: 添加新游戏

## 触发

- "添加一个新游戏"
- "实现 xxx 游戏"
- "add game"

## 操作步骤

### 1. 创建游戏设计文档

在 `docs/design/` 中创建设计文件，包含：
- 游戏概述和玩法规则
- 命令列表
- 领域模型
- 状态流转图

### 2. 创建游戏子包

```
src/main/java/com/github/wechat/ilink/bot/<game>/
├── <Game>Game.java        # 游戏入口类
├── handler/                # 命令处理器
│   ├── XxxCommand.java
│   └── ...
└── model/                  # 游戏领域模型
    ├── Xxx.java
    └── ...
```

### 3. 实现游戏入口类

```java
public class PetGame {
    private final CommandRegistry registry;
    private final SessionManager sessions;

    public PetGame(CommandRegistry registry, SessionManager sessions) {
        this.registry = registry;
        this.sessions = sessions;
    }

    public void register() {
        registry.register(new FeedCommand(sessions));
        registry.register(new PlayCommand(sessions));
        // ...
    }
}
```

### 4. 实现命令处理器

每个命令实现 `Command` 接口，参照 `docs/conventions/command-pattern.md`。

### 5. 实现领域模型

游戏专属模型放在 `model/` 子包中，不与其他游戏共享。

### 6. 注册游戏

在 `GameApplication.start()` 中添加：

```java
new PetGame(registry, sessions).register();
```

### 7. 编写测试

- 每个命令处理器一个测试类
- 测试不依赖 SDK
- 覆盖正常流程和错误情况

## 注意事项

- 新游戏不引用其他游戏的包
- 使用 `PlayerSession` 的 gameState 存储游戏状态，通过 key 区分
- 命令名不应与已有游戏冲突（可用前缀区分）
