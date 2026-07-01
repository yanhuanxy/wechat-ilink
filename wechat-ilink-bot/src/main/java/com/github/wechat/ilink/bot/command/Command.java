package com.github.wechat.ilink.bot.command;

import com.github.wechat.ilink.bot.session.PlayerSession;

public interface Command {

    String name();

    String description();

    CommandResult execute(PlayerSession session, String[] args);
}
