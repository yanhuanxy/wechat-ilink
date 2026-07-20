# ADR-0002：发布 wechat-ilink-sdk 到 Maven Central

- 状态：🟡 已决策、执行中（2026-07-20）
- 相关：[0001](0001-no-reactive-incremental-dispatch-decoupling.md)（JDK17/SDK3.0 基线）、bot [ADR-0002](../../wechat-ilink-bot/docs/adr/0002-jdk17-sdk3-upgrade.md)

## 背景

SDK 已升至 `3.0.0`（JDK17 基线，ADR-0001），但**未发布到 Maven Central**：

- `bot/pom.xml` 把依赖写成固定 `io.github.lith0924:wechat-ilink-sdk:3.0.0`（非 SNAPSHOT），当前构建**完全依赖本地 `.m2` 的手装 3.0.0**；
- `bot/.github/workflows/ci.yml` 注释曾误述"SDK 3.0.0 已发布到 Maven Central"——干净 CI 环境必然解析失败（已纠正注释）；
- bot 开源计划（全新历史）阻塞：外部用户 `git clone` + `mvn package` 拉不到不在 Central 的 SDK → bot 对外不可构建。

## 决策

**将 `wechat-ilink-sdk` 发布到 Maven Central**，使坐标 `io.github.lith0924:wechat-ilink-sdk:3.0.0` 公开可解析。

## 理由

1. **平台路线**（bot ROADMAP）：让 iLink SDK 生态被外部开发者用起来，SDK 在 Central 是前置。
2. **bot 开源解锁**：bot pom 已按"在 Central"假设写，发 Central 是唯一自洽终态。
3. **坐标自洽**：消费方（bot / imoney）无需私服或本地 install 即可构建。

## 影响 / 代价

- **一次性发布工程**：Sonatype Central Portal 账号 + GPG 签名密钥 + pom 补 `<distributionManagement>`/`nexus-staging`/`gpg`/`source`/`javadoc` 插件与 Central 强制元信息（name/url/licenses/developers/scm）。
- **groupId 命名空间验证**：`io.github.lith0924` 须由发布者拥有的 GitHub 账号 `lith0924` 经 Sonatype 验证；**待确认归属**（见待办）。
- **发布节奏**：破坏性 API 变更后须同步发新版并维护升级指南；`2.3.3` 作为 JDK8 LTS 保留。

## 分工

- **SDK 侧（已完成/进行中）**：纠正 ci.yml 误述、清理 `ilink-sdk.properties` 死键、补 pom 发布 profile 与元信息、新增 LICENSE、README 加 2.x→3.0 升级指南、本 ADR。
- **发布者侧（owner，无法代办）**：Sonatype Central Portal 账号注册、GPG 密钥生成与上传、`mvn -P release deploy` 实际发布、CI secrets 配置。

## 待办

1. ~~确认 `io.github.lith0924` 归属~~ ✅ 已决（2026-07-20）：改用 `io.github.yanhuanxy`（发布者自有账号）；SDK/bot pom 与文档已迁移，imoney 暂不动（继续旧坐标 `io.github.lith0924:2.3.3`，后续单独决策）。
2. 确定 license（MIT / Apache-2.0），新增 LICENSE 文件并填 pom `<licenses>`。
3. 填 pom `<developers>`/`<scm>` 准确值。
4. 发布前给 CI 补 SDK 预装步骤（或私服），直至 Central 可解析。
