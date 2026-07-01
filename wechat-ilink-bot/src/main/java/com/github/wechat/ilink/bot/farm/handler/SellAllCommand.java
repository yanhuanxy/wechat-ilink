package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.farm.model.Inventory;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.Map;

public class SellAllCommand implements Command {

    @Override
    public String name() { return "SELL_ALL"; }

    @Override
    public String description() { return "一键卖菜"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        Inventory inv = session.getInventory();
        if (inv.getProduce().isEmpty()) {
            return CommandResult.error("背包中没有可卖的产出");
        }

        int totalGold = 0;
        int totalItems = 0;
        StringBuilder detail = new StringBuilder();

        for (Map.Entry<String, Integer> entry : inv.getProduce().entrySet()) {
            Crop crop = CropRegistry.get(entry.getKey());
            if (crop != null) {
                int gold = crop.getSellPrice() * entry.getValue();
                totalGold += gold;
                totalItems += entry.getValue();
                detail.append(crop.getName()).append(" x").append(entry.getValue())
                        .append(" = ").append(gold).append("金币\n");
            }
        }

        inv.getProduce().clear();
        session.addGold(totalGold);

        StringBuilder sb = new StringBuilder();
        sb.append("💰 卖菜结果\n");
        sb.append(detail);
        sb.append("总计: ").append(totalItems).append("件，获得 ").append(totalGold).append(" 金币");
        return CommandResult.success(sb.toString());
    }
}
