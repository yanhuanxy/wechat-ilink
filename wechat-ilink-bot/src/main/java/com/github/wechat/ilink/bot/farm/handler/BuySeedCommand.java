package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Crop;
import com.github.wechat.ilink.bot.farm.model.CropRegistry;
import com.github.wechat.ilink.bot.session.PlayerSession;

public class BuySeedCommand implements Command {

    @Override
    public String name() { return "BUY_SEED"; }

    @Override
    public String description() { return "购买种子"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            return CommandResult.error("请指定作物名称，如：#购买 小麦 或 #购买 小麦 x3");
        }

        String rawArg = args[0].trim();
        String cropName;
        int wantCount = 1;

        int lastSpace = rawArg.lastIndexOf(' ');
        if (lastSpace > 0) {
            cropName = rawArg.substring(0, lastSpace).trim();
            String countPart = rawArg.substring(lastSpace + 1).trim();
            if (countPart.toLowerCase().startsWith("x")) {
                try {
                    wantCount = Integer.parseInt(countPart.substring(1));
                } catch (NumberFormatException e) {
                    return CommandResult.error("数量格式错误，正确格式：x3");
                }
            } else {
                cropName = rawArg;
            }
        } else {
            cropName = rawArg;
        }

        if (wantCount <= 0) {
            return CommandResult.error("购买数量必须大于0");
        }

        Crop crop = CropRegistry.getByName(cropName);
        if (crop == null) {
            return CommandResult.error("未知作物: " + cropName + "，输入'#商店'查看可购买的种子");
        }

        int unitPrice = crop.getBuyPrice();
        int gold = session.getGold();
        int affordable = gold / unitPrice;

        if (affordable <= 0) {
            return CommandResult.error("金币不足！" + crop.getEmoji() + " " + crop.getName() + "种子 " +
                    unitPrice + " 金币/个，当前 " + gold + " 金币");
        }

        int bought;
        String extraMsg;
        if (affordable >= wantCount) {
            bought = wantCount;
            extraMsg = "";
        } else {
            bought = affordable;
            extraMsg = "（购买" + wantCount + "个金币不足，已购买可负担的 " + bought + " 个）";
        }

        int totalCost = unitPrice * bought;
        session.spendGold(totalCost);
        session.getInventory().addSeed(crop.getKey(), bought);

        StringBuilder sb = new StringBuilder();
        sb.append("🛒 购买成功！\n");
        sb.append(crop.getEmoji()).append(" ").append(crop.getName()).append("种子 x").append(bought);
        if (!extraMsg.isEmpty()) {
            sb.append(extraMsg);
        }
        sb.append("\n💰 花费: ").append(totalCost).append(" 金币");
        sb.append("\n💵 余额: ").append(session.getGold()).append(" 金币");
        return CommandResult.success(sb.toString());
    }
}
