package com.github.wechat.ilink.bot.command;

public class ParsedCommand {

    private final String name;
    private final String[] args;

    public ParsedCommand(String name, String[] args) {
        this.name = name;
        this.args = args != null ? args : new String[0];
    }

    public String getName() {
        return name;
    }

    public String[] getArgs() {
        return args;
    }
}
