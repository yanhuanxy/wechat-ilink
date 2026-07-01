# 错误处理

## 异常层级

```
GameException (基础异常)
  ├── CommandParseException       # 文本无法解析为命令
  ├── UnknownCommandException     # 命令名未注册
  ├── InvalidArgumentException    # 命令参数错误（空、越界、格式不对）
  ├── GameStateException          # 无效游戏状态（空地块收获、重复种植）
  └── SessionExpiredException     # 会话过期
```

## 命令处理器错误处理

命令处理器不抛异常给用户，而是返回 `CommandResult.error()`：

```java
public CommandResult execute(PlayerSession session, String[] args) {
    if (args.length < 1) {
        return CommandResult.error("请指定要种植的作物名称");
    }

    FarmPlot plot = session.getPlot(plotId);
    if (plot == null) {
        return CommandResult.error("地块 " + plotId + " 不存在");
    }

    if (plot.hasCrop()) {
        return CommandResult.error("地块 " + plotId + " 已种植作物，请先收获或锄地");
    }

    // 正常逻辑...
    return CommandResult.success("成功种植小麦！");
}
```

## GameBot 异常兜底

`GameBot.onMessages` 对 `router.route()` 包 try/catch，任何 `BotMode` 抛异常都被 catch-all 兜底，确保用户始终收到回复：

```java
@Override
public void onMessages(List<WeixinMessage> messages) {
    for (WeixinMessage msg : messages) {
        try {
            ModeOutcome outcome = router.route(msg);
            if (outcome.getStatus() == HANDLED && outcome.getErrorMessage() != null) {
                client.sendText(msg.getFrom_user_id(), outcome.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("消息处理出错, userId={}", msg.getFrom_user_id(), e);
            try { client.sendText(userId, "出了点问题，请稍后再试"); } catch (Exception ignored) {}
        }
    }
}
```

各 `BotMode` 内部还有自己的细粒度错误处理（如 ChatMode 区分 LLM 错误与队列拒绝、FarmMode 命令错误回 `#帮助`），不会被外层 catch-all 覆盖。错误消息经 `ctx.sender()`（`RetrySender` 装饰，带发送重试）回发。

## 错误消息原则

- 用户只看到**友好的中文提示**，绝不暴露异常类名或堆栈
- 日志记录完整异常信息（包含 userId、commandName）
- `CommandResult.error()` 的消息直接展示给用户
- SDK 异常由 `GameBot` 捕获并记录日志

## 禁止

- 禁止向用户输出 `NullPointerException`、`StackOverflowError` 等
- 禁止吞掉异常（空 catch 块）
- 禁止在命令处理器中 catch 后不返回 CommandResult
