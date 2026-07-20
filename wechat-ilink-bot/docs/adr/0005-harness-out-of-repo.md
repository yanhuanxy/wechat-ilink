# ADR-0005 Claude harness 保持库外，AGENTS.md 是唯一入库契约

- 状态：✅ 生效中（决策确认于 2026-07-15）
- 权威约束位置：根 [.gitignore](../../../.gitignore)（全局 `.claude/` 与 `CLAUDE.md` 两行）

## 背景

根仓库 `.gitignore` 以无锚定规则全局忽略所有层级的 `.claude/` 与 `CLAUDE.md`。后果是 bot+sdk 的整个 Claude Code 专属层——根级子代理（product-manager、java-security-reviewer）、product-review 技能、bot 的 settings/hooks/rules/skills、各 CLAUDE.md——只存在于本机磁盘，不进 git 历史；而各项目 `AGENTS.md` 正常入库。2026-07 的 harness 架构评审将其列为"版本控制边界与 harness 边界不对齐"提请决策。

## 决策

**维持现状，刻意为之**：Claude 专属层（`.claude/` + `CLAUDE.md`）保持库外；`AGENTS.md` 是唯一入库的 AI 契约（工具无关，任何 AI 工具可读）。开源发布（只发 bot、全新历史）天然不携带 harness。独立仓库 imoney 镜像同一口径（其 `.gitignore` 忽略 `.claude/` 与 `CLAUDE.md`，`AGENTS.md` 入库）；autogame 在此决策前已全量跟踪其 harness，属该仓库既成事实，不回改。

## 理由

1. harness 含个人化工作流（permissions、hooks、子代理种子知识），与开源仓库的受众无关，入库反而要为公开可读性做脱敏维护。
2. AGENTS.md 承载全部可移植契约，协作者/其它 AI 工具凭它即可工作，Claude 专属层缺失不阻断开发。

## 影响 / 代价（已接受）

- **单点丢失风险**：harness 无 git 保护，备份责任在本机（换机/误删无法从仓库恢复）。
- **依赖倒挂**：`data/claude-home/.claude/skills/piano-practice-review/`（SkillInstaller 安装产物，产品运行需要）被跟踪，而 `wechat-ilink-bot/.claude/skills/` 下的源副本被忽略——产物在库、源在库外，改技能须手动保持两份同步。
- **守卫四副本**：PreToolUse Bash 守卫（阻断 `git push`/`mvn deploy`/`rm -rf`）在根级与 bot/sdk/imoney 各存一份逐字相同的内联副本。Claude Code 项目 settings 按会话启动目录加载，无继承机制，四份都必须存在；修改守卫须四处同步。
