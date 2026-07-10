# 农场社交设计（偷菜 / 排行 / 昵称）

> 状态：**已落地，正式内容方向**（2026-07-10 从 ROADMAP 原「降优先级」追认）。
> 命令规范见 [command-spec.md](../reference/command-spec.md)，农场基础设计见 [farm-game.md](farm-game.md)。

## 概述

农场社交是帮帮农场的留存玩法层，在单机种植循环之上引入跨玩家互动：偷别人的菜、上榜比拼、起昵称展示。共 **7 个命令**（`STEAL` / `RANK_MENU` / `WEALTH_RANK` / `LEVEL_RANK` / `STEAL_RANK` / 已有 `PEST_RANK` / `WEED_RANK` / `WATER_RANK` 的社交化重用 + `RENAME`），由 `farm/service/StealService` 编排、`persistence/StealRecordRepository` 落账。

## 设计目标：跨玩家无锁

农场刷盘是**整表重写**（`FarmPlotRepository` 把玩家全部地块一次性写回）。若偷菜时直接改受害者的会话内存态或地块行，会被受害者下一次刷盘覆盖；若跨玩家嵌套加锁，在多 bot 线程下会死锁。

因此偷菜守一条核心不变式：

> **偷菜只读受害者的地块、只写 `steal_record` 表，绝不锁/改受害者的会话或地块。**

被偷量与补偿都记在 `steal_record` 里，由**受害者自己收获时**读本表闭环扣减——天然无死锁、无需跨玩家锁。

## 偷菜机制（`STEAL` / `#偷菜`）

两步交互（因裸数字回复不走 `#` 路由，无法像命令那样带前缀触发）：

1. `#偷菜` — `StealService.listCandidates`：从全服抽样 `SAMPLE_POOL=20` 个非自己的活跃玩家，过滤出有成熟作物的，取前 `CANDIDATE_COUNT=3` 个列出（昵称 + 作物），候选缓存 `CANDIDATE_TTL_MS=5min`。
2. `#偷菜 <序号>` — `StealService.stealByIndex`：校验序号 + 冷却后，对候选中第 N 位执行偷取。

偷取规则（`doSteal` / `pickBest`）：
- `pickBest`：在受害者地块中选**价值最高**且尚可偷（未偷光、本贼未偷过）的成熟地块。
- 偷走量：`qty = min(剩余可偷, max(1, round(产量 × STEAL_RATIO)))`，`STEAL_RATIO=0.3`。
- 成功后：偷菜者背包加收获物、按偷得价值写入**偷菜大盗榜**（`action_rank.STEAL`）、设 `COOLDOWN_MS=5min` 冷却。
- 重复防：`steal_record` 以 `(victim_id, plot_index, planted_at, thief_id)` 唯一，`INSERT OR IGNORE` 冲突返回 false → "你已经偷过这块地啦"。
- 冷启动：随机池无成熟作物 → "现在没有可偷的成熟作物，过会儿再来～"。

## 数据模型（`steal_record` 表）

| 列 | 说明 |
|----|------|
| `victim_id` | 被偷者 userId |
| `plot_index` | 地块编号 |
| `planted_at` | 该轮种植时间戳（成熟周期标识） |
| `thief_id` | 偷菜者 userId |
| `amount` | 被偷走数量 |
| `compensation` | 系统给被偷者的补偿金币（收获时到账） |
| `stolen_at` | 偷取时间 |

独立于 `farm_plot` 的会话刷盘（后者全量重写会覆盖直接改的地块行）。

## 被偷补偿与收获闭环

- **补偿**：偷得价值（`qty × sellPrice`）的 `COMPENSATION_RATIO=0.3`，记入 `steal_record.compensation`，**不**在偷菜瞬间改受害者金币（守无锁不变式）。
- **收获闭环**（`HarvestAllCommand`）：
  1. `sumStolen(victim, plot, plantedAt)` → 从该地块产量中**扣减**被偷量；
  2. `sumCompensation(...)` → 把累计补偿**发放**给受害者；
  3. `clearPlot(victim, plot)` → 清理该地偷菜记录（跨成熟周期）。
- **展示**：`#农场`（`VIEW_FARM`）末尾追加"被偷提醒"（`sumStolenByVictim`）与"补偿待到账"（`sumCompensationByVictim`）。
- **被偷通知**：`CommandResult.data` 带 `victimNotifyUserId` / `victimNotifyText`，`FarmMode` 据此尽力给被偷者推一条通知；被偷者近期不活跃则跳过（补偿仍在收获时到账）。

## 排行榜系统

榜单统一显示昵称（无昵称用 `农夫#<wxid尾4位>` 兜底），取前 10 名。数据源：

| 榜单 | 数据源 | 写入点 |
|------|--------|--------|
| 财富榜 `WEALTH_RANK` | `player.gold` | — |
| 等级榜 `LEVEL_RANK` | `player.level` / `exp` | — |
| 偷菜大盗榜 `STEAL_RANK` | `action_rank.STEAL` | `#偷菜` 成功 |
| 除虫榜 `PEST_RANK` | `action_rank.PEST` | `#除虫` |
| 除草榜 `WEED_RANK` | `action_rank.WEED` | `#锄地` |
| 浇水榜 `WATER_RANK` | `action_rank.WATER` | `#浇水` |

`RANK_MENU`（`#排行`）为总入口。劳动类榜单由 `ActionRankRepository` 写分，财富/等级榜由 `PlayerRepository` 直查。

## 昵称系统（`RENAME` / `#改名`）

SDK 不提供微信昵称，社交展示需玩家自设。`#改名 <昵称>`（1–12 字符）写入 `PlayerSession.nickname`，榜单、偷菜候选/通知、`#我的信息` 优先显示昵称，未设置时 `农夫#<wxid尾4位>` 兜底。读取经 `PlayerRepository.getNickname(userId)`。

## 相关类

| 类 | 职责 |
|----|------|
| `farm/service/StealService` | 偷菜编排（候选 / 偷取 / 冷却），守跨玩家无锁不变式 |
| `farm/handler/StealCommand` | `#偷菜` 入口，分发到 `StealService` |
| `farm/handler/RankMenuCommand` | 排行总入口 |
| `farm/handler/{Wealth,Level,Steal,Pest,Weed,Water}RankCommand` | 各榜单渲染（`RankFormatter`） |
| `farm/handler/RenameCommand` | 昵称设置 |
| `farm/RankFormatter` / `FarmDisplay` | 榜单格式化 / 昵称展示 |
| `persistence/StealRecordRepository` | `steal_record` 表的 7 个查询 |
| `persistence/ActionRankRepository` | 劳动类榜单分值 |

## 验证与迭代（P7 留存实验）

社交玩法已上线但缺数据验证。最低成本验证：用现有审计日志（`logs/io/<userId>/io.<date>.log`，SiftingAppender 已落）离线统计偷菜/排行命令的调用频次与日活影响。
- **成功判据**：偷菜/排行日均 > 5 次/活跃用户，且整体日活提升 > 10% → 持续投入（好友关系、更多榜单）。
- **失败判据**：< 2 次/天或无变化 → 降级为实验性、停止扩展。

后续待扩展：好友关系（定向偷/互访）、防刷与经济平衡调参（`STEAL_RATIO` / `COMPENSATION_RATIO` / 冷却时长）。
