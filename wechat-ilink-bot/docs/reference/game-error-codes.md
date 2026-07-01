# 游戏错误代码

## 异常层级

```
GameException
  ├── CommandParseException        # CMD_001
  ├── UnknownCommandException      # CMD_002
  ├── InvalidArgumentException     # CMD_003
  ├── GameStateException           # GAME_001 ~ GAME_010
  └── SessionExpiredException      # SES_001
```

## 错误代码表

### 命令解析错误（CMD_xxx）

| 代码 | 异常 | 用户消息 | 场景 |
|------|------|---------|------|
| CMD_001 | CommandParseException | "无法识别的命令格式，请输入 #帮助 查看帮助" | 空输入、格式错误 |
| CMD_002 | UnknownCommandException | "未知命令: {name}\n输入 #帮助 查看可用命令" | 命令名未注册 |
| CMD_003 | InvalidArgumentException | "参数错误: {detail}" | 参数缺失、格式错误、越界 |

### 游戏状态错误（GAME_xxx）

| 代码 | 场景 | 用户消息 |
|------|------|---------|
| GAME_001 | 在非空地块种植 | "该地块已有作物，请先收获或锄地" |
| GAME_002 | 收获未成熟作物 | "作物还未成熟，请耐心等待" |
| GAME_003 | 收获已枯萎作物 | "作物已枯萎，请先锄地清理" |
| GAME_004 | 收获空地块 | "该地块是空地，没有可收获的作物" |
| GAME_005 | 金币不足 | "金币不足！需要 {price} 金币，当前只有 {balance}" |
| GAME_006 | 背包已满 | "背包已满！请先出售或使用物品" |
| GAME_007 | 物品不存在 | "物品 {name} 不存在" |
| GAME_008 | 物品数量不足 | "{name} 数量不足！需要 {need}，当前只有 {have}" |
| GAME_009 | 地块不存在 | "地块 {id} 不存在（共 {total} 块）" |
| GAME_010 | 今日已签到 | "今天已经签到过了，明天再来吧！" |

### 会话错误（SES_xxx）

| 代码 | 场景 | 用户消息 |
|------|------|---------|
| SES_001 | 会话过期 | "会话已过期，已重新初始化" |

### SDK 错误处理

SDK 异常由 `GameBot` 捕获，不暴露给用户：

| SDK 异常 | 处理方式 | 用户消息 |
|---------|---------|---------|
| ILinkException (网络) | 记录日志，重试一次 | "网络不稳定，请稍后重试" |
| ILinkException (登录) | 记录日志，尝试重新登录 | "系统维护中，请稍后" |
| ILinkException (发送) | 记录日志 | 不重试（用户会重发） |

## 恢复策略

- 所有命令错误返回 `CommandResult.error()`，附带友好消息
- 会话过期自动创建新会话
- SDK 异常由 GameBot 兜底，用户看到统一提示
- 不向用户暴露任何异常类名或内部错误代码
