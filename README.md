# wechat-ilink 工作区

> **English** · Workspace for the WeChat iLink ecosystem: a Java SDK for the WeChat iLink bot protocol, a multi-mode reference bot built on it, plus an optional companion project (mini-program automation over MCP). Each subproject carries its own README — start with [wechat-ilink-bot/README.md](wechat-ilink-bot/README.md).
> 下方为中文主体。**本页只做导航，不复制子项目细节**——细节以各子项目 README 为准。

## 项目一览

| 项目 | 是什么 | 技术栈 | 状态 | 入口 |
|------|--------|--------|------|------|
| [wechat-ilink-bot](wechat-ilink-bot/) | 多模式微信机器人（集成中枢 / 参考应用）：Chat / Farm / Claude Bridge / Review / Autogame | Java 17 · Maven · SQLite | 在研主战场 | [README](wechat-ilink-bot/README.md) · [docs/](wechat-ilink-bot/docs/) · [AGENTS](wechat-ilink-bot/AGENTS.md) |
| [wechat-ilink-sdk-java](wechat-ilink-sdk-java/) | 微信 iLink Bot 协议 SDK（`io.github.lith0924:wechat-ilink-sdk:3.0.0`），bot 的地基 | Java 17 · Maven | 开源主战场 | [README＝API 手册](wechat-ilink-sdk-java/README.md) · [AGENTS](wechat-ilink-sdk-java/AGENTS.md) |
| [wechat-link-autogame-xcx](wechat-link-autogame-xcx/) | 视觉流程自动化（图像识别驱动小程序）+ MCP 服务端，bot 的 `!` 模式经 MCP 调它 | Python 3.13 · PyQt6 · 仅 Windows | 在研（独立仓库） | [README](wechat-link-autogame-xcx/README.md) · [doc/](wechat-link-autogame-xcx/doc/) · [AGENTS](wechat-link-autogame-xcx/AGENTS.md) |

## 生态关系

```
wechat-ilink-sdk-java (io.github.lith0924:wechat-ilink-sdk)   ← 地基（bot 经 Maven 依赖引用）
        ▲  封装微信 PC 客户端：扫码登录、长轮询、收发文本/图/文件/视频、CDN+AES
        │
wechat-ilink-bot                                              ← 集成中枢 / 参考应用
        │  多模式路由 + 会话 + 持久化 + LLM + 可靠性
        │
        └── ! / MCP ──►  wechat-link-autogame-xcx（独立仓库，可选）← Python：图像识别 + 指令调度
```

## 快速上手

想把机器人跑起来 → 看 [wechat-ilink-bot/README.md](wechat-ilink-bot/README.md) 的「快速上手」：`mvn clean package` + 扫码即可，零 API key 可玩农场模式。

## 仓库边界

- **本仓库**跟踪 `wechat-ilink-bot`、`wechat-ilink-sdk-java` 与 bot 的运行时配置目录 `data/`（模板与开关入库；真实密钥/会话状态由 [.gitignore](.gitignore) 排除）。
- `wechat-link-autogame-xcx` 是**并列目录下的独立 git 仓库**，不属于本仓库。

## 注意

- 工作区行为准则见根 `CLAUDE.md`（含子项目文档地图）；各子项目契约以其 `AGENTS.md` 为唯一真相源。
- 注意子项目技术基线差异：bot/sdk 是 **JDK 17、禁 Spring/Lombok**；autogame 是 **Python 3.13**——规则不可跨项目套用。
