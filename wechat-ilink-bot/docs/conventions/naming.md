# 命名约定

## 包命名

全小写，按业务模块组织：
```
com.github.wechat.ilink.bot.engine
com.github.wechat.ilink.bot.command
com.github.wechat.ilink.bot.session
com.github.wechat.ilink.bot.persistence
com.github.wechat.ilink.bot.llm
com.github.wechat.ilink.bot.util
com.github.wechat.ilink.bot.farm.handler
com.github.wechat.ilink.bot.farm.model
```

## 类命名

| 类型 | 模式 | 示例 |
|------|------|------|
| 应用入口 | XxxApplication | `GameApplication` |
| SDK 桥接 | XxxBot | `GameBot` |
| 引擎 | XxxEngine | `GameEngine` |
| 解析器 | XxxParser | `CommandParser` |
| 渲染器 | XxxRenderer | `ResponseRenderer` |
| 管理器 | XxxManager | `SessionManager`, `ChatHistoryManager` |
| 注册表 | XxxRegistry | `CommandRegistry` |
| 仓库 | XxxRepository | `PlayerRepository`, `ActionRankRepository` |
| 配置 | XxxConfig | `LlmConfig` |
| Provider | XxxProvider | `LlmProvider`, `OpenAiProvider` |
| 命令接口 | Command | `Command` |
| 命令结果 | CommandResult | `CommandResult` |
| 命令处理器 | 动词 + Command | `PlantAllCommand`, `HarvestAllCommand` |
| 会话 | XxxSession | `PlayerSession` |
| 领域模型 | 名词 | `Crop`, `FarmPlot`, `Inventory` |
| 异常 | XxxException | `CommandParseException`, `GameStateException` |

## 方法命名

动词-宾语结构：
```java
getOrCreate()     // 获取或创建
findByName()      // 按名称查找
registerCommand() // 注册命令
dispatchCommand() // 分发命令
parseText()       // 解析文本
renderResult()    // 渲染结果
```

## 常量命名

全大写下划线分隔：
```java
private static final int MAX_SESSION_TIMEOUT_MINUTES = 30;
private static final String COMMAND_PREFIX = "/";
```

## 变量命名

驼峰命名：
```java
PlayerSession session;
CommandRegistry commandRegistry;
String userId;
```
