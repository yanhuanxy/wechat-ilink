# 命令模式

## Command 接口

```java
public interface Command {
    String name();
    String description();
    CommandResult execute(PlayerSession session, String[] args);
}
```

## CommandResult

```java
public class CommandResult {
    private final boolean success;
    private final String message;           // 用户可见消息
    private final Map<String, Object> data; // 附带数据（渲染用）

    public static CommandResult success(String message) {
        return new CommandResult(true, message, Collections.emptyMap());
    }

    public static CommandResult success(String message, Map<String, Object> data) {
        return new CommandResult(true, message, data);
    }

    public static CommandResult error(String message) {
        return new CommandResult(false, message, Collections.emptyMap());
    }
}
```

## 命令注册

通过 `CommandRegistry` 注册，游戏入口类负责注册该游戏的所有命令：

```java
public class FarmGame {
    private final CommandRegistry registry;
    private final ActionRankRepository rankRepo;

    public FarmGame(CommandRegistry registry, ActionRankRepository rankRepo) {
        this.registry = registry;
        this.rankRepo = rankRepo;
    }

    public void registerCommands() {
        registry.register(new UserInfoCommand());
        registry.register(new ViewFarmCommand());
        registry.register(new HelpCommand());
        // ... 共 27 个命令

        // 注册中文别名
        registry.registerAlias("我的信息", "USER_INFO");
        registry.registerAlias("签到", "CHECKIN");
        // ... 共 47 个别名
    }
}
```

## 命令解析

`CommandParser` 将文本解析为命令名 + 参数（`#` 前缀由 `ModeRouter` 剥离后交 `FarmMode` → `GameEngine`，Parser 不感知前缀）：

```java
public class CommandParser {
    private final CommandRegistry registry;

    public ParsedCommand parse(String rawText) {
        String trimmed = rawText.trim();

        // 精确别名匹配
        String commandName = registry.resolveAlias(trimmed);
        if (commandName != null) {
            return new ParsedCommand(commandName, new String[0]);
        }

        // 空格分隔: "购买 小麦 x3" → alias="购买", args=["小麦 x3"]
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

## 命令实现示例

```java
public class PlantAllCommand implements Command {

    @Override
    public String name() {
        return "PLANT_ALL";
    }

    @Override
    public String description() {
        return "在所有空地块种植指定作物";
    }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        if (args.length < 1) {
            return CommandResult.error("请指定要种植的作物名称");
        }

        String cropName = args[0];

        // 游戏逻辑...
        return CommandResult.success("成功种植" + cropName + "！");
    }
}
```

## 帮助文本生成

`CommandRegistry` 可生成所有已注册命令的帮助文本：

```java
public String helpText() {
    return commands.values().stream()
        .map(c -> c.name() + " - " + c.description())
        .collect(Collectors.joining("\n"));
}
```
