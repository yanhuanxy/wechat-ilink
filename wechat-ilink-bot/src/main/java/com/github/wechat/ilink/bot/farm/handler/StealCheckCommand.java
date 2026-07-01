package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.session.PlayerSession;

public class StealCheckCommand implements Command {

    @Override
    public String name() { return "STEAL_CHECK"; }

    @Override
    public String description() { return "偷菜查询"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        return CommandResult.success("🔍 偷菜功能暂未开放，敬请期待！");
    }
}
