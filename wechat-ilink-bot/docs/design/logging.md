# 日志落地设计

统一 SLF4J + Logback，配置在 `src/main/resources/logback.xml`。日志根目录 `${LOG_DIR:-logs}`（项目根 `logs/`，已被 `.gitignore` 忽略）。所有文件 appender 按日期滚动、`maxHistory=30`、UTF-8 编码。

## 三类落地

| 类别 | Appender | 路径 | 触发 |
|------|----------|------|------|
| **系统日志** | `SYSTEM_FILE`（RollingFile） | `logs/system/system.<date>.log` | root logger（INFO），全部 SLF4J 日志 |
| **收发审计** | `MSG_IO_SIFT`（SiftingAppender，按 MDC `userId` 分流） | `logs/io/<userId>/io.<date>.log` | logger `MSG_IO`（`additivity=false`） |
| **Claude 桥接** | `CLAUDE_FILE`（RollingFile） | `logs/claude/claude.<date>.log` | logger `com.github.wechat.ilink.bot.mode.claude` + `...mode.ClaudeBridgeMode` |

console（`STDOUT`）保留，挂在 root，开发期仍可见。

## 收发审计（按用户按日期）

核心是 **MDC + SiftingAppender**：

- `util/MessageAuditLog`（框架层）持有专用 logger `MSG_IO`，提供：
  - `inbound(userId, text)` — 入向（用户 → bot）
  - `outbound(userId, kind, text)` — 出向（bot → 用户），`kind` ∈ `text`/`image`/`file`/`video`
  - 每次调用 `MDC.put("userId", sanitize(userId))` → `log` → `finally MDC.remove`，保证线程安全、不泄漏 MDC。
  - `sanitize()` 把非 `[A-Za-z0-9_-]` 替换为 `_`（防 userId 含 `/`、`:` 破坏文件路径），null/空 → `unknown`。
- logback `SiftingAppender` 以 MDC key `userId` 为 discriminator，为每个用户创建独立的 `RollingFileAppender`，落到 `logs/io/<userId>/`。

### 埋点位置

- **入向**：`mode/ModeRouter.route(...)` 提取 text 后调 `MessageAuditLog.inbound(userId, text)`（命令/视频/文件路径之外的常规文本路径）。
- **出向**：`GameBot` 的 `ModeSender` 实现方法（`sendText`/`sendTextWithTyping`/`sendImage`/`sendFile`/`sendVideo`）各调 `outbound(...)`。所有 `BotMode` 经 `ctx.sender()`（`RetrySender` → `GameBot`）发送，因此统一在 `GameBot` 收口。

> ModeRouter 在 framework 层，仅 import `util/MessageAuditLog`，仍保持 `mode/` 包零 SDK import。

## Claude 桥接日志（生命周期 + 错误）

无需改代码：`ClaudeCodeAdapter` / `ClaudeBridgeMode` 现有的 `log.info/warn/error`（子进程启动、结束 exitCode、耗时、超时强杀、API 错误）已覆盖「生命周期 + 错误」粒度，仅通过 logback 把这两个 logger 额外路由到 `CLAUDE_FILE`（`additivity` 默认 true，仍进 console/系统日志）。子进程逐行 stdout/stderr **不落盘**。

## 安全约束

- **botToken / contextToken 等敏感凭证绝不写日志**（仅入 SQLite `bot_session`）。`MSG_IO` 只记消息文本，由埋点处控制。

## 配置覆盖

`LOG_DIR` 可经 JVM 系统属性或环境变量覆盖（如 `-DLOG_DIR=/var/log/ilink-bot`），默认 `logs`。
