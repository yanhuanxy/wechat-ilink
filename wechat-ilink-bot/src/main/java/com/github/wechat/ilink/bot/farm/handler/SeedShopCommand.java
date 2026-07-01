package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;

import java.util.List;

public class SeedShopCommand implements Command {

    @Override
    public String name() { return "SEED_SHOP"; }

    @Override
    public String description() { return "种子商店"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        List<Crop> crops = CropRegistry.all();
        StringBuilder sb = new StringBuilder();
        sb.append("🏪 种子商店\n");
        int i = 1;
        for (Crop crop : crops) {
            sb.append(i++).append(". ").append(crop.getEmoji())
                    .append(" ").append(crop.getName()).append("种子")
                    .append(" - ").append(crop.getBuyPrice()).append("金币")
                    .append(" (").append(crop.getGrowTimeMinutes()).append("min)\n");
        }
        sb.append("\n💡 使用 #购买 作物名 购买种子，如 #购买 小麦");
        return CommandResult.success(sb.toString().trim());
    }
}
