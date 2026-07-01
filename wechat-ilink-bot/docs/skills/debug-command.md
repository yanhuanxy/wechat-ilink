# Skill: 调试命令处理

## 触发

- "xxx 命令不工作"
- "命令报错"
- "debug command"

## 诊断步骤

### 1. 检查日志中的解析结果

查看日志输出，确认 CommandParser 是否正确解析：

```
DEBUG - 命令解析: rawText="plant wheat 1" → name="plant", args=["wheat", "1"]
```

如果未看到此日志，问题在消息接收层（GameBot）。

### 2. 验证 CommandRegistry 包含该命令

```java
// 在测试中
assertNotNull(registry.find("plant"), "plant 命令未注册");
assertNotNull(registry.find("种植"), "种植 别名未注册");
```

如果为 null，检查游戏入口类的 `register()` 是否被调用。

### 3. 检查会话状态

确认会话是否正确初始化：

```java
PlayerSession session = sessionManager.getOrCreate("testUser");
assertNotNull(session);
```

如果会话为 null 或过期，检查 SessionManager 配置。

### 4. 验证命令处理器参数

在命令处理器中添加 DEBUG 日志：

```java
log.debug("命令执行: name={}, args={}, session={}", name(), Arrays.toString(args), session);
```

确认 args 内容符合预期。

### 5. 检查 CommandResult

确认 CommandResult 包含正确的消息和数据：

```java
CommandResult result = command.execute(session, args);
log.debug("命令结果: success={}, message={}", result.isSuccess(), result.getMessage());
```

### 6. 验证 ResponseRenderer 输出

检查渲染结果是否符合微信消息约束：

```java
String rendered = renderer.render(result);
log.debug("渲染结果: length={}, content={}", rendered.length(), rendered);
```

如果长度超过 4096，需要消息拆分。

## 常见问题

| 症状 | 可能原因 | 排查 |
|------|---------|------|
| "未知命令" | 命令未注册或名称拼写错 | 检查 registry.find() |
| 命令执行但无响应 | 渲染器返回空或发送失败 | 检查 renderer.render() 和日志 |
| 状态未保存 | persist 未被调用 | 检查 GameEngine.dispatch() |
| 并发问题 | 未使用每用户锁 | 检查 `SessionManager.lockFor(userId)` 的每用户锁（命令处理在锁内，发送在锁外） |
