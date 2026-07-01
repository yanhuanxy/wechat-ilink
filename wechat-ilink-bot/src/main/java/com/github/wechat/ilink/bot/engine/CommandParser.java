package com.github.wechat.ilink.bot.engine;

import com.github.wechat.ilink.bot.command.CommandRegistry;
import com.github.wechat.ilink.bot.command.ParsedCommand;

public class CommandParser {

    private final CommandRegistry registry;

    public CommandParser(CommandRegistry registry) {
        this.registry = registry;
    }

    public ParsedCommand parse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ParsedCommand("UNKNOWN", new String[0]);
        }

        String trimmed = rawText.trim();

        String commandName = registry.resolveAlias(trimmed);
        if (commandName != null) {
            return new ParsedCommand(commandName, new String[0]);
        }

        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            String prefix = trimmed.substring(0, spaceIdx);
            String rest = trimmed.substring(spaceIdx + 1).trim();
            commandName = registry.resolveAlias(prefix);
            if (commandName != null) {
                return new ParsedCommand(commandName, rest.isEmpty() ? new String[0] : new String[]{rest});
            }
        }

        return new ParsedCommand("UNKNOWN", new String[]{trimmed});
    }
}
