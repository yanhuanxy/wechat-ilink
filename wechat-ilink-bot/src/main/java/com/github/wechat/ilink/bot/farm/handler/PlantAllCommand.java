package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.farm.model.CropStage;
import com.github.wechat.ilink.bot.farm.model.FarmPlot;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.List;

public class PlantAllCommand implements Command {

    @Override
    public String name() { return "PLANT_ALL"; }

    @Override
    public String description() { return "一键种植"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        String cropName = (args.length > 0 && !args[0].isEmpty()) ? args[0] : session.getLastCropKey();
        if (cropName == null) {
            return CommandResult.error("请指定作物名称，如：#种植 小麦");
        }

        Crop crop = CropRegistry.getByName(cropName);
        if (crop == null) {
            return CommandResult.error("未知作物: " + cropName);
        }

        List<FarmPlot> activePlots = session.getActivePlots();
        int planted = 0;
        for (FarmPlot plot : activePlots) {
            if (plot.getStage() == CropStage.EMPTY) {
                if (!session.getInventory().useSeed(crop.getKey())) {
                    break;
                }
                plot.plant(crop.getKey());
                planted++;
            }
        }

        if (planted == 0) {
            return CommandResult.error("没有空地或没有足够的" + crop.getName() + "种子");
        }

        session.setLastCropKey(crop.getKey());
        return CommandResult.success("🌱 种植完成！在 " + planted + " 块地种上了" + crop.getName());
    }
}
