package com.github.wechat.ilink.bot.command;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CommandRegistry {

    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public void register(Command command) {
        commands.put(command.name(), command);
    }

    public void registerAlias(String alias, String commandName) {
        aliases.put(alias, commandName);
    }

    public Command find(String name) {
        return commands.get(name);
    }

    public String resolveAlias(String text) {
        if (commands.containsKey(text)) {
            return text;
        }
        return aliases.get(text);
    }

    public Set<Command> allCommands() {
        return Collections.unmodifiableSet(new HashSet<>(commands.values()));
    }
}
