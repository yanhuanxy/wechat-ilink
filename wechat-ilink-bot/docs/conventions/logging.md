# 日志规范

## Logger 声明

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameEngine {
    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);
}
```

## 日志级别

| 级别 | 使用场景 |
|------|---------|
| ERROR | 异常捕获、不可恢复错误 |
| WARN | 可恢复的异常、接近阈值 |
| INFO | 关键业务事件（消息接收、命令执行、会话创建） |
| DEBUG | 详细流程（解析结果、状态变更） |

## 结构化日志

日志必须包含业务上下文（userId、commandName）：

```java
// 正确
log.info("命令执行: userId={}, command={}, duration={}ms", userId, commandName, duration);
log.error("处理消息失败: userId={}, error={}", userId, e.getMessage(), e);

// 错误 — 缺少上下文
log.info("command executed");
log.error("error", e);
```

## 敏感数据

**禁止记录以下内容**：
- botToken
- contextToken
- 完整的消息原文（仅记录命令名）
- 用户微信号、昵称等隐私信息

```java
// 正确
log.info("消息接收: userId={}, command={}", userId, parsedCommand.getName());

// 错误 — 记录了完整消息
log.info("消息接收: text={}", msg.getContent());
```

## 异常日志

异常必须附带完整堆栈：

```java
log.error("处理消息失败: userId={}, command={}", userId, commandName, e);
// 最后一个参数是异常对象，SLF4J 会输出完整堆栈
```
