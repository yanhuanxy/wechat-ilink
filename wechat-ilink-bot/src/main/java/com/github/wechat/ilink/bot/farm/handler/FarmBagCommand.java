package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.farm.model.Inventory;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.Map;

public class FarmBagCommand implements Command {

    @Override
    public String name() { return "FARM_BAG"; }

    @Override
    public String description() { return "农场背包"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        Inventory inv = session.getInventory();
        int total = inv.totalSeedCount() + inv.totalProduceCount();

        StringBuilder sb = new StringBuilder();
        sb.append("📦 农场背包 (").append(total).append("件)\n");

        if (!inv.getSeeds().isEmpty()) {
            sb.append("\n🌱 种子:\n");
            for (Map.Entry<String, Integer> entry : inv.getSeeds().entrySet()) {
                Crop crop = CropRegistry.get(entry.getKey());
                String name = crop != null ? crop.getName() : entry.getKey();
                sb.append("  ").append(name).append("种子 x").append(entry.getValue()).append("\n");
            }
        }

        if (!inv.getProduce().isEmpty()) {
            sb.append("\n🌾 产出:\n");
            for (Map.Entry<String, Integer> entry : inv.getProduce().entrySet()) {
                Crop crop = CropRegistry.get(entry.getKey());
                String name = crop != null ? crop.getName() : entry.getKey();
                sb.append("  ").append(name).append(" x").append(entry.getValue()).append("\n");
            }
        }

        if (inv.getSeeds().isEmpty() && inv.getProduce().isEmpty()) {
            sb.append("\n背包是空的");
        }
        return CommandResult.success(sb.toString().trim());
    }
}
