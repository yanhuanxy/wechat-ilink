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

        String trimmed = normalize(rawText);

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

    private static final String TRAILING_PUNCT = "。！？，、；：";

    /** 预处理：全角空格归一、去首尾空白、剥尾随中文标点（仅作用于命令体，不进参数中间）。 */
    private static String normalize(String rawText) {
        String s = rawText.replace('　', ' ').trim();
        int end = s.length();
        while (end > 0 && TRAILING_PUNCT.indexOf(s.charAt(end - 1)) >= 0) {
            end--;
        }
        return end == s.length() ? s : s.substring(0, end);
    }
}
