# 架构决策记录（ADR）

一页一决策：背景 → 决策 → 理由 → 影响。**记录"为什么"，代码只能记录"是什么"。**

- 新增重大架构/技术选型决策（含 NO-GO）时在此加一篇并更新本索引（见 `.claude/rules/doc-maintenance.md` 映射表）。
- 决策被推翻时不删旧文，在旧文顶部标 `已被 XXXX 取代` 并链新文。
- 编号递增；回填的历史决策标注"回填"，日期不精确处如实写约数。

## 索引

| 编号 | 决策 | 状态 |
|------|------|------|
| [0001](0001-no-spring-di-lombok.md) | 禁止 Spring / DI 框架 / Lombok，构造器注入 + 唯一组合根 | ✅ 生效中（回填） |
| [0002](0002-jdk17-sdk3-upgrade.md) | JDK 8 → 17 升级，SDK 升 3.0.0；imoney 暂不跟进 | ✅ 生效中（回填） |
| [0003](0003-video-review-dashscope.md) | 视频点评从 Claude Code 子进程改走 DashScope HTTP 直连 | ✅ 生效中（回填） |
| [0004](0004-remote-orchestration-into-bot.md) | 远程驱动与 LLM 编排从 autogame-xcx 迁入 bot（MCP 客户端模式） | ✅ 生效中（回填） |
| [0005](0005-harness-out-of-repo.md) | Claude harness（.claude/ + CLAUDE.md）保持库外，AGENTS.md 是唯一入库契约 | ✅ 生效中 |

> Claude Bridge 逐次工具审批的 **NO-GO 决策**（2026-07-09）已完整记录于 [../design/claude-bridge-phase3.2-spike.md](../design/claude-bridge-phase3.2-spike.md)，实质是一篇 ADR，不再重复建档。
