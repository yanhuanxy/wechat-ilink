package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.farm.model.CropStage;
import com.github.wechat.ilink.bot.farm.model.FarmPlot;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.List;

public class FertilizeCommand implements Command {

    @Override
    public String name() { return "FERTILIZE"; }

    @Override
    public String description() { return "施肥"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        String cropName = (args.length > 0 && !args[0].isEmpty()) ? args[0] : session.getLastCropKey();
        if (cropName == null) {
            return CommandResult.error("请指定作物类型，如：#施肥 小麦");
        }

        if (!session.getInventory().useTool("fertilizer")) {
            return CommandResult.error("没有肥料道具，可通过签到或点券商店获取");
        }

        Crop crop = CropRegistry.getByName(cropName);
        if (crop == null) {
            return CommandResult.error("未知作物: " + cropName);
        }

        List<FarmPlot> activePlots = session.getActivePlots();
        int fertilized = 0;
        for (FarmPlot plot : activePlots) {
            if (plot.getCropType() != null && plot.getCropType().equals(crop.getKey())
                    && plot.getStage() != CropStage.EMPTY && plot.getStage() != CropStage.WITHERED) {
                if (plot.getPlantedAt() != null) {
                    plot.setPlantedAt(plot.getPlantedAt() - 10 * 60 * 1000L);
                }
                fertilized++;
            }
        }

        if (fertilized == 0) {
            session.getInventory().addTool("fertilizer", 1);
            return CommandResult.error("没有种植" + crop.getName() + "的地块");
        }

        session.setLastCropKey(crop.getKey());
        return CommandResult.success("🧪 施肥成功！加速了 " + fertilized + " 块" + crop.getName() + "的生长");
    }
}
