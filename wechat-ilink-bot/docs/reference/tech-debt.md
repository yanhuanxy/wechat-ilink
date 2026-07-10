# 技术债清单

> 集中记录已知技术债与处置策略，避免散落在各文档。随处置进展更新。门面见 [README](../../README.md)，路线见 [ROADMAP](../ROADMAP.md)。

## 测试债（P2 工程化主攻）

| 项 | 现状 | 处置 |
|----|------|------|
| 整体覆盖率 | **77.4%**（`McpClient` 纯逻辑 50%，剩余网络路径 + 组合根 + HTTP provider 待补），低于 80% 红线 | 组合根集成测试 + McpClient 可注入 transport + HTTP provider mock |
| `McpClient.java` | 50%（纯逻辑已测；剩余真实 SSE/HTTP 路径无 live 服务难单测） | 引入可注入 transport，覆盖网络路径 |
| Farm 排行漏测 | `LevelRankCommand` / `PestRankCommand` / `WeedRankCommand` 零直接测试（同批 Wealth/Steal/Water/RankMenu 有） | 复制 `FarmSocialTest` 模板补 3 个 |
| 持久层独立测试缺口 | `StealRecordRepository` / `FarmPlotRepository` / `InventoryRepository` / `PlayerRepository` / `MessageDedupRepository` 无独立测试；`ActionRankRepository` 仅 1 极浅用例 | 逐个补，`MessageDedupRepository`（可靠性关键路径）优先 |

## 架构债

| 项 | 现状 | 处置 |
|----|------|------|
| SDK 防腐层缺失 | `mode/{ModeRouter,ReviewMode,MediaDownloader}` + `task/TaskMessageHandler` 直接 import SDK 模型（`WeixinMessage`/`MessageItem`/`VideoItem`），无 Anti-Corruption Layer | **推迟**。Phase 0 dry-run（2026-07-10）确认 SDK 2.3.3→3.0.0 消息模型 API **零破坏性差异**（仅 `ILinkClient` 新增 `requestQRCode()`），现实升级破坏面为零。待 SDK 出现破坏性变更或第二个 SDK 时再做 ACL（YAGNI） |
| 超限文件 | `BotInstance.java`(448) / `GameApplication.java`(406) 超 400 行红线；`ClaudeCodeAdapter.java`(400) 临界 | 不痛不拆，加功能受阻时优先拆装配/适配类 |
| `PlayerSession` God Object | 295 行，gold/exp/level/maxPlots/coupon/nickname/lastCheckin/checkinStreak/plots 等 8+ 字段并存 | 持续膨胀时拆 `FarmState` / `Profile` / `CheckinState` |

## 重复造轮子

| 项 | 现状 | 处置 |
|----|------|------|
| `ObjectMapper` 重复实例化 | 13 个类各自 `private static final ObjectMapper MAPPER = new ObjectMapper()` | 抽共享 `JsonUtil` 工具类 |
| 限流/冷却模式重复 | `RateLimiter.windows` / `StealService.cooldowns` / `ShareCommand.lastShareTime` 三处重复"key→时间戳→过期判" | 抽 `TimestampCache` / `Cooldown` 工具 |
| 配置类 `load()`+`createTemplate()` 重复 | `AutogameConfig` / `HookConfig` / `ReliabilityConfig` / `LlmConfig` / `TaskConfig` / `ModelsConfig` 几乎一样的加载逻辑 | 抽 `JsonConfigLoader<T>` 基类 |

> 以上重复项均为"不痛的卫生问题"，功能正常；按 YAGNI，遇痛点或新配置类时顺手收敛。

## 已知小瑕疵

| 项 | 现状 | 处置 |
|----|------|------|
| `GameApplication.shutdown()` | `mcpClient.close()` 被调两次（幂等无害，但不洁） | 去重 |
| `McpToolRegistry` | 注释残留"未来加 scheduler"（已由 `McpHealthMonitor` 实现） | 清理注释 |

## SDK 升级记录（防腐层决策依据）

| 升级 | 消息模型差异 | 结论 |
|------|-------------|------|
| 2.3.3 → 3.0.0（2026-07） | `WeixinMessage`/`MessageItem`/`VideoItem` **零差异**；`ILinkClient` 仅新增 `requestQRCode()` | 防腐层推迟 |

> 验证方式：`javap -public` 对比两版 jar 的公共 API + `mvn -o compile` 编译通过（bot 用到的所有字段/方法在 3.0.0 均兼容）。
