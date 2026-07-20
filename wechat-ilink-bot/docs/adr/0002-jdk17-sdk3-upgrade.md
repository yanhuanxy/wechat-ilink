# ADR-0002 JDK 8 → 17 升级 + SDK 升 3.0.0

- 状态：✅ 生效中（回填记录，决策于 2026-07 上旬落地）
- 权威约束位置：[AGENTS.md](../../AGENTS.md) 技术基线表；`pom.xml` `maven.compiler.release=17`

## 背景

项目初期基线为 JDK 1.8 + SDK 2.3.3。Java 8 语法在新代码（record 型 DTO、模式匹配的路由分支、text block 的长文案）上摩擦明显；SDK 侧同步演进出 3.0.0（JDK 17）。

## 决策

SDK（wechat-ilink-sdk-java）与 bot 同步升 JDK 17 / SDK 3.0.0；**imoney 暂不跟进**，保持 JDK 8 + SDK 2.3.3。

## 理由

1. bot 与 SDK 同仓同主开发，一次升级两侧验证成本最低；升级后全量测试与 live 集成测试全绿后才切换。
2. Java 17 LTS 语法（var/record/sealed/text block/switch 模式匹配）对新代码可读性收益直接。
3. imoney 是已上线生产服务且基于 SpringBlade 3.5.0 脚手架（Spring Boot 2.7.1），升 JDK 需连带升 Spring Boot 大版本，风险/收益不成比例——**显式决定不动**。

## 影响 / 代价

- 运行环境要求 JDK 17+（README/CONTRIBUTING/CI 已同步；enforcer 强制）。
- 工作区内两套 Java 基线并存（bot/sdk=17，imoney=8），跨项目改码时不可混用语法——已写入根 CLAUDE.md 跨项目红线。
- SDK 3.0.0 暂以内部使用为主，对外发布节奏另行决策。
