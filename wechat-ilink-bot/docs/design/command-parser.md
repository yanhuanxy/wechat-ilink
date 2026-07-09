# CommandParser 设计

## 职责

将 `ModeRouter` 剥离 `#` 前缀后（交 `FarmMode` → `GameEngine`）的文本解析为结构化的命令：

```
"签到"               → ParsedCommand("CHECKIN", [])
"种植 小麦"           → ParsedCommand("PLANT_ALL", ["小麦"])
"购买 小麦 x3"        → ParsedCommand("BUY_SEED", ["小麦 x3"])
"帮助"               → ParsedCommand("HELP", [])
```

注意：`#` 前缀由 GameBot 在调用 `engine.dispatch()` 前剥离，CommandParser 收到的文本不含 `#`。

## 核心实现

```java
public class CommandParser {
    private final CommandRegistry registry;

    public ParsedCommand parse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ParsedCommand("UNKNOWN", new String[0]);
        }

        String trimmed = normalize(rawText);   // 全角空格归一 + 去首尾空白 + 剥尾随中文标点

        // 精确别名匹配
        String commandName = registry.resolveAlias(trimmed);
        if (commandName != null) {
            return new ParsedCommand(commandName, new String[0]);
        }

        // 空格分隔: 第一个空格前为命令别名，空格后整体作为 args[0]
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            String prefix = trimmed.substring(0, spaceIdx);
            String rest = trimmed.substring(spaceIdx + 1).trim();
            commandName = registry.resolveAlias(prefix);
            if (commandName != null) {
                return new ParsedCommand(commandName,
                    rest.isEmpty() ? new String[0] : new String[]{rest});
            }
        }

        return new ParsedCommand("UNKNOWN", new String[]{trimmed});
    }
}
```

## 中文命令支持

中文命令直接作为别名注册：

```java
registry.registerAlias("签到", "CHECKIN");
registry.registerAlias("种植", "PLANT_ALL");
registry.registerAlias("购买", "BUY_SEED");
```

## 设计要点

- `#` 为指令触发前缀，由 GameBot 处理，Parser 不感知
- 入口预处理（`normalize`）：全角空格 `U+3000` 归一为半角、去首尾空白、剥尾随中文标点 `。！？，、；：`（仅作用于命令体，不进参数中间；当前农场参数只有作物名和数字，无合法标点场景）
- 解析逻辑简单直接，只做别名匹配 + 空格分隔
- 空格后的内容整体作为 `args[0]`，各 Command 自行二次解析
- 解析结果不验证命令是否存在（由 GameEngine 负责查找）
