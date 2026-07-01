package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Inventory;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.Map;

public class ToolBagCommand implements Command {

    @Override
    public String name() { return "TOOL_BAG"; }

    @Override
    public String description() { return "道具背包"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        Inventory inv = session.getInventory();
        StringBuilder sb = new StringBuilder();
        sb.append("🔧 道具背包\n");

        if (inv.getTools().isEmpty()) {
            sb.append("\n没有道具");
        } else {
            for (Map.Entry<String, Integer> entry : inv.getTools().entrySet()) {
                sb.append(entry.getKey()).append(" x").append(entry.getValue()).append("\n");
            }
        }
        return CommandResult.success(sb.toString().trim());
    }
}
