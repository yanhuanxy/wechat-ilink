package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.farm.model.CropStage;
import com.github.wechat.ilink.bot.farm.model.FarmPlot;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.List;

public class ViewFarmCommand implements Command {

    @Override
    public String name() { return "VIEW_FARM"; }

    @Override
    public String description() { return "查看农场"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        List<FarmPlot> activePlots = session.getActivePlots();
        StringBuilder sb = new StringBuilder();
        sb.append("🌾 帮帮农场 - 我的土地\n");

        for (FarmPlot plot : activePlots) {
            String display = formatPlot(plot);
            sb.append(String.format("[%02d]%-18s", plot.getIndex() + 1, display));
            if ((plot.getIndex() + 1) % 3 == 0) {
                sb.append("\n");
            }
        }
        if (activePlots.size() % 3 != 0) {
            sb.append("\n");
        }

        sb.append("💰 金币: ").append(session.getGold())
                .append(" 📦 种子: ").append(session.getInventory().totalSeedCount());
        return CommandResult.success(sb.toString());
    }

    private String formatPlot(FarmPlot plot) {
        if (plot.getStage() == CropStage.EMPTY) {
            return "🟫 空地";
        }
        com.github.wechat.ilink.bot.farm.model.Crop crop = CropRegistry.get(plot.getCropType());
        String emoji = crop != null ? crop.getEmoji() : "🌱";
        String stageName = stageName(plot.getStage());
        String status = "";
        if (plot.isHasPest()) status += "🐛";
        if (plot.isHasWeed()) status += "🌿";
        return emoji + crop.getName() + "-" + stageName + status;
    }

    private String stageName(CropStage stage) {
        switch (stage) {
            case SEED: return "种子";
            case SPROUT: return "幼苗";
            case GROWING: return "生长中";
            case MATURE: return "成熟✓";
            case WITHERED: return "枯萎✗";
            default: return "";
        }
    }
}
