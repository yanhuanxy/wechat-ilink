# ADR-0001 禁止 Spring / DI 框架 / Lombok

- 状态：✅ 生效中（回填记录，决策自项目初期沿用至今）
- 权威约束位置：[AGENTS.md](../../AGENTS.md) 技术基线「禁止」行 + 硬性规则 2

## 背景

bot 是单进程、单 jar、嵌入式 SQLite 的长驻应用，部署形态是"个人机器上 `java -jar` + 扫码"。对象图规模可控（一个组合根即可装配完）。

## 决策

不引入 Spring / 任何 DI 容器 / Lombok。全部依赖经**手写构造器注入**，`GameApplication` 是唯一组合根。

## 理由

1. **可追溯**：对象图在 `GameApplication` 一处显式可见，无运行时魔法（组件扫描/代理/反射注入），排障时读代码即读装配。
2. **体积与启动**：零框架依赖使 fat jar 保持小体积、启动即毫秒级，匹配"个人部署"形态。
3. **可测性不受损**：构造器注入天然可 mock（Mockito 直接传桩），不需要容器测试支持。
4. Lombok 引入编译期插件耦合与 IDE 依赖，与"读到什么就是什么"的取向冲突；手写 getter/Builder 的成本在本项目规模下可接受。

## 影响 / 代价

- 组合根随组件增多而膨胀（`GameApplication`/`BotInstance` 传参 15+ 是已知代价），构造器重叠时用重载或参数对象缓解。
- 新贡献者若习惯 Spring 风格需适应；契约与 [docs/conventions/di.md](../conventions/di.md) 有约定说明。
- 同工作区 imoney 走 Spring 路线（SpringBlade 脚手架），两套规则**互不通用**——见其 AGENTS.md。
