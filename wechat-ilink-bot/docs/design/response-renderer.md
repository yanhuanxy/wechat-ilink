# ResponseRenderer 设计

## 职责

将 `CommandResult` 格式化为用户友好的文本：
1. 格式化成功/失败消息
2. 适配微信消息长度限制
3. 使用 emoji 增强可读性

## 微信消息约束

- 纯文本，无按钮/卡片/富文本
- 消息有长度限制（约 4096 字符）
- 支持换行符 `\n`

## 核心实现

```java
public class ResponseRenderer {

    public String render(CommandResult result) {
        if (result == null) {
            return "";
        }
        if (result.isSuccess()) {
            return result.getMessage();
        }
        return "❌ " + result.getMessage();
    }
}
```

## 设计说明

- `ResponseRenderer` 仅做简单的成功/失败格式化
- 成功时直接返回 `CommandResult.getMessage()`（命令处理器自行构建格式化输出）
- 失败时添加 ❌ 前缀
- 各命令处理器（如 `ViewFarmCommand`、`HelpCommand`）自行负责输出文本的格式化，包括 emoji、布局、对齐等
- 不存在独立的 `FarmRenderer` 类

## Emoji 使用指南

| 场景 | Emoji |
|------|-------|
| 作物/植物 | 🌾🥕🍅🌽🥬 |
| 状态-生长中 | 🌱 |
| 状态-成熟 | ✅ |
| 状态-枯萎 | ☠️ |
| 金币 | 💰 |
| 背包 | 📦 |
| 天气 | 🌤️🌧️❄️ |
| 错误 | ❌ |
| 成功 | ✨ |
| 等级 | ⭐ |

适度使用，每个响应不超过 10 个 emoji。
