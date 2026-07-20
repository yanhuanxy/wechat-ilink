# ADR-0004 远程驱动与 LLM 编排从 autogame-xcx 迁入 bot

- 状态：✅ 生效中（回填记录）
- 权威约束位置：bot 侧 `mcp/` 包 + `AutogameMode`（[AGENTS.md](../../AGENTS.md)）；autogame 侧 [AGENTS.md](../../../wechat-link-autogame-xcx/AGENTS.md) 概述「已迁出」声明
- 详细规划存档：autogame 的 `doc/plans/PLAN_02_ilink远程驱动.md`、`PLAN_03_协议化接入.md`

## 背景

autogame-xcx 早期同时承载：本地视觉自动化引擎、ilink 远程驱动（收微信指令）、LLM 自然语言编排。这使 Python 侧要重复实现 bot 已有的能力（微信会话、消息路由、LLM 接入），两个项目职责纠缠。

## 决策

autogame-xcx 收敛为**纯执行引擎 + MCP 服务端**；远程驱动与 LLM 编排迁入 bot——bot 以 `!` 前缀模式（`AutogameMode`）经 MCP 客户端（`mcp/McpClient`，JSON-RPC over HTTP+SSE）远程调用 autogame 执行模板。

## 理由

1. **单一职责**：微信接入/会话/路由/LLM 是 bot 的本职，Python 侧不再重复造轮子。
2. **协议化边界**：MCP 是标准协议，两项目间从"共享内部约定"变为"标准工具调用契约"，可独立演进、独立测试（bot 侧 McpClient 可 mock）。
3. autogame 不装也不影响 bot 其他模式（opt-in，`data/autogame-config.json` 默认关闭）。

## 影响 / 代价

- 跨进程调用引入网络失败面：由 `McpHealthMonitor`（SSE 断线自愈 + 周期刷新工具清单）兜底。
- MCP 契约变更需两侧同步（映射表见 autogame `.claude/rules/doc-maintenance.md` 与 bot `docs/design/mcp-autogame.md`）。
- 后续迭代已在此边界上叠加鉴权/调用方隔离/指标（见 git 历史「autogame MCP 鉴权 + 调用方隔离 + 指标可见」）。
