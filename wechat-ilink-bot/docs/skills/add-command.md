# Skill: 添加新命令

## 触发

- "添加一个新命令"
- "实现 xxx 命令"
- "add command"

## 操作步骤

### 1. 定义命令规范

在 `docs/reference/command-spec.md` 中添加命令定义：

```markdown
#### `command_name` / `中文别名`
- **参数**：`<参数说明>`
- **说明**：命令功能描述
- **前置**：执行前提条件
- **示例**：`command_name arg1 arg2`
- **错误**：可能的错误消息
```

### 2. 创建命令处理器

在对应游戏的 `handler/` 包中创建类：

```java
package com.github.wechat.ilink.bot.farm.handler;

public class XxxCommand implements Command {
    private final SessionManager sessions;

    public XxxCommand(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public String name() { return "xxx"; }

    @Override
    public String description() { return "功能描述"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        // 验证参数
        // 执行游戏逻辑
        // 修改会话状态
        return CommandResult.success("成功消息");
    }
}
```

### 3. 注册命令

在游戏入口类的 `register()` 方法中添加：

```java
registry.register(new XxxCommand(sessions));
registry.registerAlias("中文别名", "xxx");
```

### 4. 添加渲染逻辑（如需要）

在 `ResponseRenderer` 中添加专用的格式化方法。

### 5. 编写测试

在对应测试包创建测试类：

```java
class XxxCommandTest {
    private XxxCommand command;
    private PlayerSession session;

    @BeforeEach
    void setUp() {
        command = new XxxCommand(sessions);
        session = new PlayerSession("user123");
    }

    @Test
    void execute_validArgs_returnsSuccess() { ... }

    @Test
    void execute_invalidArgs_returnsError() { ... }
}
```

### 6. 更新文档

- 更新 `command-spec.md`（步骤 1 已完成）
- 如有新错误码，更新 `game-error-codes.md`
- 更新 `backlog.md` 标记完成
