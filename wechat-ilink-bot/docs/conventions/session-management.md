# 会话管理

## SessionManager 职责

`SessionManager` 是唯一的会话所有者，其他类通过它访问会话：

```java
public class SessionManager {
    private final ConcurrentHashMap<String, PlayerSession> sessions = new ConcurrentHashMap<String, PlayerSession>();
    private final PlayerRepository playerRepo;
    private final FarmPlotRepository plotRepo;
    private final InventoryRepository inventoryRepo;
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000L;

    public SessionManager(DatabaseManager dbManager) {
        this.playerRepo = new PlayerRepository(dbManager);
        this.plotRepo = new FarmPlotRepository(dbManager);
        this.inventoryRepo = new InventoryRepository(dbManager);
    }

    public PlayerSession getOrCreate(String userId) {
        PlayerSession cached = sessions.get(userId);
        if (cached != null) {
            if (System.currentTimeMillis() - cached.getLastActivity() > SESSION_TIMEOUT_MS) {
                sessions.remove(userId);
            } else {
                return cached;
            }
        }

        return sessions.computeIfAbsent(userId, this::loadFromDb);
    }

    private PlayerSession loadFromDb(String userId) {
        PlayerSession session = playerRepo.findById(userId);
        if (session == null) {
            session = new PlayerSession(userId);
            playerRepo.insert(session);
        } else {
            List<FarmPlot> plots = plotRepo.findByUserId(userId);
            if (!plots.isEmpty()) {
                session.setPlots(plots);
            }
            Inventory inventory = inventoryRepo.findByUserId(userId);
            session.setInventory(inventory);
            session.clearDirty();
        }
        return session;
    }

    public void saveSession(PlayerSession session) {
        if (!session.isDirty()) {
            return;
        }
        playerRepo.update(session);
        plotRepo.replaceByUserId(session.getUserId(), session.getPlots());
        inventoryRepo.replaceByUserId(session.getUserId(), session.getInventory());
        session.clearDirty();
    }

    public void flushAll() {
        for (PlayerSession session : sessions.values()) {
            saveSession(session);
        }
    }

    public void remove(String userId) {
        sessions.remove(userId);
    }
}
```

## PlayerSession 结构

```java
public class PlayerSession {
    private final String userId;
    private int gold;          // 初始 500
    private int exp;           // 初始 0
    private int level;         // 初始 1
    private int maxPlots;      // 初始 4，最大 36
    private int coupon;        // 初始 0
    private String lastCheckin;
    private int checkinStreak;
    private List<FarmPlot> plots;    // 36 块
    private Inventory inventory;
    private BotModeType currentMode; // 当前模式，默认 BotModeType.CHAT
    private transient String activeClaudeSessionId; // 活跃 Claude 会话，仅内存态，不持久化
    private transient boolean claudePrivileged;      // /sudo 提权标志，仅内存态，重启回收
    private transient boolean claudePlanMode;        // /plan 计划档标志，与 claudePrivileged 互斥，重启回收
    private transient boolean claudeApprovedExec;    // /approve 一次性执行标志，下一条消息消费；切会话/重启清除
    private transient int claudeTurnCount;           // 活跃会话累计轮次，达阈值触发自动 /compact
    private long lastActivity;
    private boolean dirty;

    public void touchActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public void setCurrentMode(BotModeType mode) {
        this.currentMode = mode;
        this.dirty = true;
    }

    // 不标记 dirty、不入库——重启后为 null，靠 /sessions + /resume 恢复
    // 切换到不同会话（含 /new 置 null、/resume 切换、新会话首次赋 id）时轮次归零 + 清除 approved 执行标志；同 id 续传不重置
    public void setActiveClaudeSessionId(String sessionId) {
        if (sessionId == null ? activeClaudeSessionId != null : !sessionId.equals(activeClaudeSessionId)) {
            this.claudeTurnCount = 0;
            this.claudeApprovedExec = false;
        }
        this.activeClaudeSessionId = sessionId;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - lastActivity > SESSION_TIMEOUT_MS;
    }
}
```

## 模式状态持久化

`currentMode` 字段通过 `PlayerRepository` 持久化到 `player.bot_mode` 列（TEXT，存枚举 name）。

| 流程 | 说明 |
|------|------|
| 切换模式 | `/mode chat` → `SystemCommandMode` → `session.setCurrentMode(CHAT)`（标记 dirty）→ `sessions.saveSession()` → `UPDATE player SET bot_mode = 'CHAT'` |
| 重启恢复 | `SessionManager.getOrCreate(userId)` → `playerRepo.findById()` → `session.setCurrentMode(BotModeType.fromName(row.bot_mode))` |
| 新用户 | `playerRepo.insert()` 写入默认值 `'CHAT'` |

`DatabaseManager.migratePlayerBotMode()` 在 `initialize()` 中通过 `PRAGMA table_info` 检查列是否存在，缺失则 `ALTER TABLE player ADD COLUMN bot_mode TEXT`，保证旧库平滑升级。

## Claude 会话持久化

Claude Bridge 模式有两层会话状态，刻意分离：

| 状态 | 存储 | 生命周期 | 说明 |
|------|------|---------|------|
| 活跃会话 ID（`activeClaudeSessionId`） | `PlayerSession` 的 transient 字段（内存） | 进程内、跨消息存活；**重启即丢** | 决定下一条消息是否带 `--resume`；不标记 dirty、不入库 |
| 提权标志（`claudePrivileged`） | `PlayerSession` 的 transient 字段（内存） | 进程内；**重启回收** | 管理员 `/sudo on` / `/approve` 置位，决定子进程受限/提权档；不入库 |
| plan 标志（`claudePlanMode`） | `PlayerSession` 的 transient 字段（内存） | 进程内；**重启回收** | `/plan on` 置位，子进程以 `--permission-mode plan` 只读产出方案；与 `claudePrivileged` 互斥；不入库 |
| 执行标志（`claudeApprovedExec`） | `PlayerSession` 的 transient 字段（内存） | 一次性；切会话/重启清除 | `/approve` 置位，下一条消息拼"执行上一轮计划"前缀后消费；不入库 |
| 累计轮次（`claudeTurnCount`） | `PlayerSession` 的 transient 字段（内存） | 跟随活跃会话；切换会话归零 | 每条成功回复自增；达 `claudeBridgeCompactThreshold` 触发自动 `/compact` |
| 会话元数据 | `claude_sessions` 表（`ClaudeSessionRepository`） | 持久化 | `session_id / user_id / cwd / model / title / created_at / updated_at` |
| 消息去重水位线 | `processed_message` 表（`MessageDedupRepository`） | 持久化 | 每用户已处理最大 `message_id`；`GameBot.onMessages` 据此跳过重启 resume 重投的旧消息，避免旧问题被重跑 |

`claude_sessions` 表结构（`DatabaseManager.executeSchema()` 中幂等建表）：

```sql
CREATE TABLE IF NOT EXISTS claude_sessions (
  session_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, cwd TEXT, model TEXT,
  title TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS idx_claude_sessions_user ON claude_sessions(user_id, updated_at DESC);
```

| 流程 | 说明 |
|------|------|
| 首条消息 | `activeClaudeSessionId == null` → 无 `--resume` → claude 返回 `session_id` → `repository.insert(...)` + `setActiveClaudeSessionId(id)` |
| 后续消息 | `activeClaudeSessionId != null` → `--resume <id>` → `repository.touchUpdatedAt(id)` 刷新排序 |
| `/new` | `setActiveClaudeSessionId(null)`，下一条消息重新建会话 |
| 重启后恢复 | 活跃会话丢失 → `/sessions` 列出历史 → `/resume <序号>` 重新设置 `activeClaudeSessionId` |

## 会话生命周期

```
创建 → 活跃使用 → 过期/移除
  │         │           │
  ▼         ▼           ▼
首次消息  每次命令    30分钟无活动
computeIfAbsent  touchActivity() → 缓存移除，下次重新加载
```

## 线程安全

- `SessionManager` 使用 `ConcurrentHashMap` 做缓存
- 每用户锁在 `SessionManager` 中管理（`lockFor/withLock`），`GameEngine` 与 `FlushGate` 异步 flush 共用：
  ```java
  // GameEngine.dispatch() 中
  synchronized (sessionManager.lockFor(userId)) {
      PlayerSession session = sessionManager.getOrCreate(userId);
      session.touchActivity();
      CommandResult result = command.execute(session, args);
      sessionManager.scheduleFlush(userId);   // 经 FlushGate 刷盘（默认同步立即刷）
  }
  ```
- 锁下沉到 `SessionManager` 是为了让异步 flush（`FlushGate`/周期兜底）能复用同一把锁，读到一致快照。
- 消息发送在锁外执行
- 不同用户的操作并行执行

## 持久化

- SQLite 作为持久化存储（唯一数据源）
- `ConcurrentHashMap` 作为内存缓存（加速访问）
- 懒加载：首次访问时通过 Repository 从 SQLite 加载，而非启动时批量加载
- 命令/模式变更后经 `scheduleFlush(userId)` 刷盘（`FlushGate`）：默认 `flushDelayMs=0` 同步立即写（仅在 `isDirty()` 时，等价旧行为）；`flushDelayMs>0` 时 per-user debounce 合并突发写
- `FlushGate` 周期（`flushIntervalMs`，默认 30s）兜底 flush 所有 dirty 会话，崩溃丢失 ≤ 该窗口
- 关闭时调用 `sessionManager.shutdown()`（= 全量 flush + 停 FlushGate）确保所有会话持久化
- 落盘在 `SessionManager` 的每用户锁内执行，保证线程安全

```java
// 懒加载 — 通过三个 Repository 组装
PlayerSession loadFromDb(String userId) {
    PlayerSession session = playerRepo.findById(userId);
    if (session == null) {
        session = new PlayerSession(userId);
        playerRepo.insert(session);
    } else {
        session.setPlots(plotRepo.findByUserId(userId));
        session.setInventory(inventoryRepo.findByUserId(userId));
        session.clearDirty();
    }
    return session;
}

// 关闭时刷写
public void shutdown() {
    sessionManager.shutdown();   // 全量 flush + 停 FlushGate
    dbManager.close();
}
```

## 过期策略

- 默认 30 分钟无活动过期
- 过期后从缓存移除，下次交互从 SQLite 重新加载
