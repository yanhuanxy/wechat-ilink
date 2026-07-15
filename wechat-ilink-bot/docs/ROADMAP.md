# 项目路线图（ROADMAP）

> 定位、现状与未来方向。取代旧的 `backlog.md`（农场功能清单）。门面见 [README.md](../README.md)。

## 项目地位

`wechat-ilink-bot` 是 **iLink 生态的集成中枢 / 参考应用**：

```
wechat-ilink-sdk-java          地基：微信 PC 客户端封装（登录/收发/CDN+AES）
        ▲
   wechat-ilink-bot             本项目：多模式路由 + 会话 + 持久化 + LLM + 可靠性（平台能力的展示与编排层）
        ├──! / MCP──►  wechat-link-autogame-xcx   小程序自动化（图像识别 + 调度）
        └(规划)────►  wechat-ilink-imoney         对话记账（未来 BotMode 联动）
```

- **SDK 是地基**，bot 是其上最完整的应用——证明 iLink 能支撑多账号、多模式、AI 子进程、远程 MCP 的复杂编排。
- bot 不做屏幕识别 / 记账等业务，而是**编排**：路由用户意图到合适的执行体（LLM / 游戏引擎 / claude 子进程 / MCP server）。

## 已完成里程碑

| 里程碑 | 内容 |
|--------|------|
| Phase 0 | 框架核心（engine/command/session/persistence/util）+ 帮帮农场 27 命令（含偷菜/排行/昵称社交，见 [farm-social.md](design/farm-social.md)）+ SQLite WAL |
| LLM | OpenAI 兼容 Provider + 流式/同步 + 滑动窗口历史 + per-user 背压队列 |
| Task | 视频 Claude Code 任务流（ProcessBuilder + stream-json）+ DashScope 视频模型 + 工作目录隔离 |
| Phase 1 | 多模式路由地基（`ModeRouter` + `BotMode` + 5 模式枚举 + `/mode` 持久化） |
| Phase 2 | Claude Bridge（`--resume` 跨消息会话 + `claude_sessions` 表 + `/new`/`/sessions`/`/resume`） |
| Phase 3 | 工具审批静态策略（`allowedTools`/`disallowedTools` + `permissionMode`，Review/Bridge 共用） |
| Phase 4 | 双向文件回传（`MediaDownloader` 入向 + `ModeSender.sendFile/sendVideo` 出向 + `BridgeFileBuffer`/`BridgeWorkspace`） |
| autogame | `!` 模式 + MCP 客户端（JSON-RPC over HTTP+SSE）驱动 autogame-xcx |
| Phase 5 | 可靠性增强（`RetrySender`/`RateLimiter`/`McpHealthMonitor`/`FlushGate` + 锁下沉 + `ReliabilityConfig`） |
| Phase H | Hooks 子系统（生命周期 hook：`mode/hook/` + `config/HookConfig`，H1–H4——审计/限流/错误兜底/模式切换迁为可插拔 hook，对标 Claude Code 的 PreToolUse/Stop；详见 [hooks.md](design/hooks.md)） |

## 现状盘点

- **5 模式 + 系统命令**全部可用；各子系统可独立 `enabled=false` 禁用。
- **多账号**：`data/bots.json` 多 `BotInstance` + 运行时动态加号。
- **持久化**：SQLite WAL，`FlushGate` 合并突发写 + 周期兜底，崩溃丢失 ≤ 窗口。
- **测试**：492 个单元测试（6 个 live 测试需环境变量、CI 跳过）。
- **autogame/MCP** 为 opt-in（默认 `enabled=false`，需启动 autogame-xcx 的 MCP server :8765）。

## 未来方向（按优先级）

### P1 — autogame 产品化
**为什么**：当前 MCP 仅 `localhost`、`enabled=false`、无鉴权，是个能跑的原型而非可用产品。
- 远程部署 + 传输鉴权（token / mTLS），支持多 bot 共用一个 autogame-xcx 实例。
- 模板治理：`!list`/`!run` 之外支持模板 CRUD、参数化、执行历史归档。
- 队列可观测：`pendingCount` 暴露为 `/status` 指标，长任务进度回执。

### P2 — 工程化补齐
**为什么**：覆盖率 77.4% 仍低于 80% 红线，`McpClient` 纯逻辑已测至 50%（剩余真实 SSE/HTTP 路径待可注入 transport）；CI 已建 75% 门槛，尚需组合根集成测试与 HTTP provider mock。
- CI 流水线（GitHub Actions：`mvn verify` + 覆盖率门槛）。
- Checkstyle / spotbugs 接入 Maven。
- 覆盖率回升：`McpClient` 真实 SSE/HTTP 路径（34%）经可注入 transport 重构后可达测；组合根类（`GameApplication`/`SkillInstaller`）补集成测试。

### P3 — iMoney 联动
**为什么**：生态已有对话记账服务 [wechat-link-imoney](../../wechat-ilink-imoney)，bot 却未接——天然的"对话记账"新模式。
- 新增 `AccountingMode`（BotMode）调 imoney API：自然语言 → 记账/查询/统计。
- 经 `/mode accounting` 切换，复用 ChatMode 的 LLM 编排 + imoney 的结构化存储。

### P4 — Phase 3.2 交互式工具审批 — ❌ 不做（NO-GO，2026-07-09）
**结论**：逐次工具交互审批**不实现**，权限收敛为 JVM 判定的二元制（受限只读 / admin 提权 bypass）。理由：`--permission-prompt-tool` 实为 MCP 工具名（需 JVM 反向做 MCP server）、schema/端点未验证、对已能提权者不增安全、远程侧损效率。真正的低摩擦提权由现有 `/sudo` 逐会话档承担，另加 opt-in 旋钮 `claudeBridgeAdminDefaultPrivileged`（admin 进入即默认提权）。详见 [claude-bridge-phase3.2-spike.md](design/claude-bridge-phase3.2-spike.md) 决策更新。

### P5 — MCP 泛化
**为什么**：当前 MCP 客户端仅绑 autogame-xcx 的 5 个 tool。
- 泛化为通用 MCP 客户端：接更多 MCP server（文件系统 / 搜索 / 自定义工具），按 `ModeContext` 注入多个 registry。

### P6 — 可观测性 / 多实例运维
- 结构化指标（消息量 / 模式分布 / MCP 调用延迟 / 重试次数）导出。
- 多实例下会话与配置的隔离/共享策略。

### P7 — 农场社交（已落地，持续扩展）
**为什么**：偷菜 / 排行 / 昵称已实现并上线，是农场核心留存玩法——从原「降优先级、非平台方向」**追认为正式内容方向**（2026-07-10）。设计见 [farm-social.md](design/farm-social.md)。
- **现状**：偷菜两步交互 + 全服随机池、6 类榜单（财富 / 等级 / 偷菜 / 除虫 / 除草 / 浇水）、昵称系统、被偷补偿机制、`steal_record` 跨玩家无锁设计。
- **待扩展**：好友关系（定向偷 / 互访）、更多社交榜单、防刷与经济平衡调参。
- **验证**：用现有审计日志（`logs/io/<userId>/`）统计偷菜 / 排行命令频次与日活影响，据数据定迭代节奏（低成本留存实验）。

## 迭代排布（2026-07-11 对齐）

> 上下文：owner 已选**平台路线**（让 iLink SDK 生态被外部开发者用起来），产能 **1 人业余（每周 5–10h）**，autogame 继续做。本节把上面的 P1–P7 落到可执行的迭代序列。

**约束**：业余带宽极薄，严格**串行**推进，禁止并行两条线（双烂尾风险）。每轮留 20% buffer。

| 迭代 | 状态 | 周期 | 主线 | 关联 ROADMAP | 验收 |
|------|------|------|------|--------------|------|
| **A** | ✅ 已完成（2026-07-11） | 3–4w | CI + checkstyle + 覆盖率回升 | P2 | CI 绿、覆盖率 ≥75%（渐进式，先 70% 保不退化）——本地 `mvn verify` 已验证（JDK17.0.6）：549 测试全绿（4 live 跳过）、jacoco 覆盖率门禁达标、checkstyle 0 violations |
| **B** | ⏭️ 跳过，不执行（owner 决策，2026-07-11） | 4w | `AccountingMode` MVP + `/mode accounting` | P3 | 调 imoney API 走通记账闭环、≥5 单测 |
| **C.1** | ✅ 已完成（2026-07-11） | 5–6w（分 C.1/C.2） | autogame 鉴权 + 调用方隔离 + 指标可见（代码侧） | P1 | 见下方"迭代 C.1 进度" |
| **C.2** | ⏳ 待启动 | — | 真中断支持 / 实际远程部署验证 / GUI host+token 编辑 | P1 | 见下方"迭代 C.1 进度"不做项 |
| **D** | ⏳ 待启动 | 3–4w | MCP 多 server 泛化 + 指标导出 | P5 + P6 | 同时接 2 个 MCP server、消息量/模式分布/MCP 延迟可见 |

**总周期约 4–5 个月**（业余带宽），对应平台路线从"骨架稳"走到"可被外部用"。迭代 B（iMoney 联动）owner 决策跳过，直接从 A 进入 C；P3 方向本身不撤回，仅本轮迭代不排期。

**待办前置**（不属于本 ROADMAP，但卡迭代）：
- 迭代 B 启动前：与 imoney 侧对齐 API 契约（认证 / 记账查询端点 / 错误码）。无契约不开 B。
- 迭代 A 启动前：确认仓库 GitHub Actions 可用 + actions 写权限。

详见产品规划报告（plan 文件：`memoized-gathering-kay.md`，会话内产物）。

## 技术债

| 项 | 现状 | 处置 |
|----|------|------|
| `McpClient.java` 覆盖率 | 50%（纯逻辑已测；剩余真实 SSE/HTTP 路径无 live 服务难单测） | 引入可注入 transport |
| `GameApplication.shutdown()` | `mcpClient.close()` 被调用两次 | 去重（幂等无害，但不洁） |
| 整体覆盖率 | 77.4%（`McpClient` 纯逻辑 50%，剩余网络路径 + 组合根 + HTTP provider 待补） | 组合根集成测试 + McpClient 可注入 transport |
| `McpToolRegistry` | 注释残留"未来加 scheduler"（已由 `McpHealthMonitor` 实现） | 清理注释 |

### 迭代 A 进度（2026-07-11）—— ✅ 已完成

| 子项 | 状态 | 备注 |
|------|------|------|
| CI 骨架（`mvn verify` + JDK 17） | ✅ 已修正 | JDK 8→17、分支 main→master、package→verify；见 `.github/workflows/ci.yml` |
| 覆盖率门禁（jacoco check） | ✅ 已接入 | BUNDLE LINE ≥70% 起步，绑定 verify 阶段；后续提到 75%→80% |
| Checkstyle（warn-only） | ✅ 已接入 | `checkstyle.xml` + maven-checkstyle-plugin 3.3.1，`failsOnError=false`；规则对齐 CLAUDE.md 硬性规则 |
| `McpClient` 测试补充 | ✅ 已补 7 个 | message 路径 no-op 分支、close 后状态、baseUrl trim；真实 HTTP 路径需后续重构 |
| 本地验证 | ✅ 已验证通过（2026-07-11） | 本机真实 JDK17 路径为 `D:\java17.06`（非环境变量 `JAVA_HOME17`，该变量实际不存在）；`mvn verify` 549 测试全绿（4 live 跳过）、jacoco 覆盖率门禁达标、checkstyle 0 violations；过程中发现全局 `~/.m2/settings.xml` 配置的阿里云镜像域名握手失败（本机网络问题，与代码无关），改用临时 settings 直连 Maven Central 验证通过，未改动机器级配置 |

### 迭代 C.1 进度（2026-07-11）—— ✅ 已完成（代码侧）

> 跨两个独立 git 仓库：本仓库（bot 端 Java）+ `wechat-ilink-autogame-xcx`（Python MCP server）。协议契约详见 [design/mcp-autogame.md](design/mcp-autogame.md)。

| 子项 | 状态 | 备注 |
|------|------|------|
| 鉴权 Token（bot 端） | ✅ 已接入 | `AutogameConfig.authToken` + `McpClient` 构造器/SSE/POST 带 `Authorization: Bearer` |
| 鉴权中间件（Python 端） | ✅ 已接入 | `mcp/server.py` `_BearerAuthMiddleware`（`starlette.middleware.base.BaseHTTPMiddleware`），token 为空跳过校验 |
| host 可配置（远程化，Python 端） | ✅ 已接入 | `mcp/server_config.py` 读 `data/mcp_server_config.json`，`McpServerThread` 不再硬编码 `127.0.0.1` |
| 调用方隔离（`caller`） | ✅ 已接入 | bot 端 5 个 tool 调用都带 `caller`（`BotConfig.name`）；Python 端 `ExecutorBridge` 记录 owner，`get_status` 报告、`stop_execution` 越权校验 |
| `/status` 指标可见 | ✅ 已接入 | `SystemCommandMode.sendStatus` 追加 MCP 连接状态 + `pendingCount()` |
| 测试 | ✅ 已补 | bot 端 9 个新用例（`mvn verify` 558 全绿）；Python 端新增/扩展用例（`pytest tests/unit` 90 全绿），两端 lint 干净（新代码范围内） |
| 本地验证 | ✅ 已验证 | 两端各自 `mvn verify` / `pytest + ruff` 均全绿；未做跨机真实网络部署验证（不在本轮范围） |

**C.1 不做（留给 C.2，见上方迭代排布）**：`GameExecutor` 真中断支持（`stop_execution` 仍是 stub）、mTLS、per-bot 独立 token、限流/审计日志、GUI 端 host/token 可视化编辑、真实跨机网络部署验证。

## 演进原则

- **平台优先于内容**：新增能力优先以 `BotMode` / MCP tool 形式接入，而非硬编码进 `GameBot`。
- **opt-in 不破坏现状**：新子系统默认禁用，启用才生效（参考 task/autogame/reliability 的模式）。
- **`ModeContext` 是唯一扩展点**：新模式 = 加字段 + 加 Mode + 注册 Router。
- **SDK 隔离不破**：`mode/` 包零 SDK import，所有收发经 `ModeSender`/`MediaDownloader`。
- **农场进入维护期**（2026-07-11）：平台路线下，农场是"样板内容"而非主线；只修 bug，不再加新玩法（P7 农场社交维持降级）。新能力以平台扩展（新模式 / MCP server）优先。
