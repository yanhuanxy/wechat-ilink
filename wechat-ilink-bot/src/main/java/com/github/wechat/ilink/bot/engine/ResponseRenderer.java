package com.github.wechat.ilink.bot.engine;

import com.github.wechat.ilink.bot.command.CommandResult;

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
