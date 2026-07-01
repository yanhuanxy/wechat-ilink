package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.farm.model.CropStage;
import com.github.wechat.ilink.bot.farm.model.FarmPlot;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HarvestAllCommand implements Command {

    @Override
    public String name() { return "HARVEST_ALL"; }

    @Override
    public String description() { return "一键收获"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        List<FarmPlot> activePlots = session.getActivePlots();
        Map<String, Integer> harvested = new LinkedHashMap<String, Integer>();
        int totalExp = 0;
        int count = 0;

        for (FarmPlot plot : activePlots) {
            if (plot.getStage() == CropStage.MATURE) {
                Crop crop = CropRegistry.get(plot.getCropType());
                if (crop != null) {
                    session.getInventory().addProduce(crop.getKey(), crop.getYieldAmount());
                    Integer prev = harvested.get(crop.getName());
                    harvested.put(crop.getName(), (prev != null ? prev : 0) + 1);
                    totalExp += crop.getExpReward();
                    count++;
                }
                plot.harvest();
            }
        }

        if (count == 0) {
            return CommandResult.error("没有成熟的作物可以收获");
        }

        session.addExp(totalExp);

        StringBuilder sb = new StringBuilder();
        sb.append("✨ 收获结果\n");
        for (Map.Entry<String, Integer> entry : harvested.entrySet()) {
            Crop crop = CropRegistry.getByName(entry.getKey());
            int yield = crop != null ? crop.getYieldAmount() * entry.getValue() : entry.getValue();
            sb.append("收获 ").append(entry.getValue()).append("块").append(entry.getKey())
                    .append(" x").append(yield).append("个\n");
        }
        sb.append("获得经验: ").append(totalExp);
        return CommandResult.success(sb.toString());
    }
}
