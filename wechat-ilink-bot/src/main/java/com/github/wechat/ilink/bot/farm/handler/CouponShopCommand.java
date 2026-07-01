package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.session.PlayerSession;

public class CouponShopCommand implements Command {

    @Override
    public String name() { return "COUPON_SHOP"; }

    @Override
    public String description() { return "点券商店"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎟️ 点券商店\n");
        sb.append("1. 🧪 加速肥料 - 5点券 (加速1小时)\n");
        sb.append("2. 🛡️ 护盾 - 3点券 (防偷菜1次)\n");
        sb.append("3. 🌍 扩地令 - 10点券 (解锁2块地)\n");
        sb.append("\n当前点券: ").append(session.getCoupon());
        return CommandResult.success(sb.toString());
    }
}
