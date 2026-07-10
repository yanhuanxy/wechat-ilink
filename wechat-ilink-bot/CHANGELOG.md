# Changelog

本项目所有重要变更记录于此。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added（能力，开源版本所含）
- **农场社交**：偷菜（全服随机池、两步交互、被偷补偿）、6 类榜单（财富 / 等级 / 偷菜 / 除虫 / 除草 / 浇水）、昵称系统；新增 5 个命令（共 27 命令）。设计见 [docs/design/farm-social.md](docs/design/farm-social.md)。
- **Claude Bridge 模式**：本机 `claude` CLI 子进程 + `--resume` 跨消息会话 + 双向文件回传 + 二元制权限（`/sudo` 提权）。
- **Review 模式**：视频点评任务（Claude Code / DashScope 视频模型）。
- **Autogame 模式 + MCP 客户端**：`!` 前缀经 JSON-RPC over HTTP+SSE 驱动 [wechat-link-autogame-xcx](../wechat-link-autogame-xcx)。
- **可靠性层**：`RetrySender` / `RateLimiter` / `McpHealthMonitor` / `FlushGate` + 锁下沉 + 8 旋钮 `ReliabilityConfig`。设计见 [docs/design/reliability.md](docs/design/reliability.md)。
- **Hooks 子系统**：运行时生命周期 hook（H1–H4）+ 开发期 Claude Code 守卫。设计见 [docs/design/hooks.md](docs/design/hooks.md)。
- **模型 / Provider 配置收敛**：`data/models-config.json` 统一注册 providers + chat/review/bridge 引用。
- **结构化日志**：系统日志 + per-user 收发审计（SiftingAppender）+ Claude 桥接日志。
- **多账号 + 会话复用**：`data/bots.json` 多 `BotInstance` + 登录游标持久化免重扫。

### Added（开源准备）
- `LICENSE`（MIT）。
- 面向小白的快速上手与「各模式前置条件表」（见 README）。
- `run.bat` / `run.sh` 一键构建运行脚本。
- `data/*.example` 配置模板（`bots` / `models-config` / `task-config` / `reliability-config` / `autogame-config`）。
- `SECURITY.md`、`CONTRIBUTING.md`、`CHANGELOG.md`。
- 仓库级 `.gitignore`，覆盖 `data/` 运行期配置与状态。
- `docs/examples/dashscope-upload.py`（由根目录的 `demo` 文件整理归档）。

### Changed
- `pom.xml`：JDK 基线 `1.8` → `17`（启用 Java 17 语法：var/record/sealed/text block 等）；`wechat-ilink-sdk` 由 `2.4.0-SNAPSHOT` 升至 `3.0.0`（JDK17 基线；`2.3.3` 作为 JDK8 收尾版本保留在 Maven Central）。
- `pom.xml`：`wechat-ilink-sdk` 锁定到已发布版本 `2.3.3`（此前依赖未发布的 `2.4.0-SNAPSHOT`）。
- `pom.xml`：新增 `maven-shade-plugin`（可运行 fat jar）与 `exec-maven-plugin`（`mvn exec:java`）。
- `task-config` 默认 `enabled:false` / `claudeBridgeEnabled:false` / `claudeAdminUsers:[]`，避免未装 `claude` CLI 时首跑报错。

> 历史变更在 `git filter-repo` 抽取为独立仓库前，可由 `git log` 追溯；此处自开源版本起维护。
