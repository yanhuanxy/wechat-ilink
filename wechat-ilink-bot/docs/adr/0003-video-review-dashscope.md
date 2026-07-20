# ADR-0003 视频点评从 Claude Code 子进程改走 DashScope HTTP 直连

- 状态：✅ 生效中（回填记录）
- 权威约束位置：`task/` 包现状（见 [AGENTS.md](../../AGENTS.md) 包结构）；历史流程存档见 [../design/task-system.md](../design/task-system.md) 顶部说明

## 背景

Review（视频点评）最初的实现是 spawn 本地 `claude` CLI 子进程处理视频（`ClaudeCodeProvider` + `WorkspaceManager`，走订阅模式免 API key）。子进程链路带来：每任务的进程/工作区管理成本、默认 `bypassPermissions` + `--add-dir` 的权限扩大面、以及对本机 claude CLI 可用性的强依赖。

## 决策

移除 `ClaudeCodeProvider` / `WorkspaceManager`，Review 改为 `DashScopeVideoProvider` 直连 DashScope OpenAI 兼容端点（HTTP 上传 + 视频模型推理），经统一的 `TaskProvider` 接口接入。

## 理由

1. **收敛子进程面**：`ClaudeCodeAdapter`（Claude Bridge 模式）成为全项目唯一 ProcessBuilder 入口，安全审查面减半。
2. **去掉高权限子进程**：视频任务不再需要 bypassPermissions 档的本地进程。
3. HTTP 直连可控性更好：超时/重试/错误码处理走统一 HTTP 语义，而非解析子进程 stream-json。
4. `TaskProvider` 单方法接口保留了替换后端的自由度。

## 影响 / 代价

- Review 模式从"免 API key（订阅）"变为需要 DashScope API key。
- 抽帧等本地预处理仍依赖 Python 3 + ffmpeg（piano-practice-review 技能内）。
- `task-system.md` 中的 Claude Code CLI 流程降级为历史记录（文首已标注）。
