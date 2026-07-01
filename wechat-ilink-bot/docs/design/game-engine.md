# GameEngine 设计

## 职责

`GameEngine` 是中央调度器：
1. 接收 (userId, rawText)
2. 解析命令
3. 获取/创建会话
4. 分发到对应的命令处理器
5. 持久化会话到 SQLite
6. 返回 CommandResult

## 核心实现

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

    public CommandResult dispatch(String userId, String rawText) {
        // 用 SessionManager 的每用户锁，与 FlushGate 异步 flush 共用，保证落盘读到一致快照
        synchronized (sessionManager.lockFor(userId)) {
            return doProcess(userId, rawText);
        }
    }

    private CommandResult doProcess(String userId, String rawText) {
        long start = System.currentTimeMillis();
        try {
            ParsedCommand parsed = commandParser.parse(rawText);

            PlayerSession session = sessionManager.getOrCreate(userId);
            session.touchActivity();

            Command command = registry.find(parsed.getName());
            if (command == null) {
                return CommandResult.error("未知命令，输入'帮助'查看可用命令");
            }

            CommandResult result = command.execute(session, parsed.getArgs());

            if (result.isSuccess()) {
                sessionManager.scheduleFlush(userId);
            }

            return result;
        } catch (Exception e) {
            log.error("命令处理出错, userId={}", userId, e);
            return CommandResult.error("出了点问题，请稍后再试");
        } finally {
            log.debug("dispatch completed, userId={}, text={}, cost={}ms",
                    userId, rawText, System.currentTimeMillis() - start);
        }
    }
}
```

## 设计要点

- `GameEngine` 不包含任何游戏特定逻辑
- 构造器接收 3 个参数：`CommandParser`、`SessionManager`、`CommandRegistry`（不依赖 `ResponseRenderer`）
- 跨切面关注点（日志、计时、持久化）在此统一处理
- 所有命令执行经过相同路径，便于监控和调试
- `session.touchActivity()` 在获取会话后调用，更新活跃时间
- **锁下沉（Phase 5）**：每用户锁不在 `GameEngine`，而在 `SessionManager.lockFor(userId)`；`dispatch` 与 `FlushGate` 异步 flush 共用同一把锁，落盘读到一致快照
- 成功后 `scheduleFlush(userId)` 经 `FlushGate` 刷盘（默认 `flushDelayMs=0` 同步立即刷，仅在 `isDirty()` 时写库；可配 debounce 合并突发写）
- `sendText` 在锁外由 `sender`（`RetrySender` 装饰，带发送重试）发送，避免持有锁时阻塞网络 I/O
- 命令失败时不刷盘（避免保存错误状态）
- 计时日志在 `DEBUG` 级别记录
