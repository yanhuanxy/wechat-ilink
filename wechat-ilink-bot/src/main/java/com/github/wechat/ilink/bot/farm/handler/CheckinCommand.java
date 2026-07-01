package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CheckinCommand implements Command {

    @Override
    public String name() { return "CHECKIN"; }

    @Override
    public String description() { return "每日签到"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        if (today.equals(session.getLastCheckin())) {
            return CommandResult.error("今日已签到，明天再来吧！");
        }

        String yesterday = new SimpleDateFormat("yyyy-MM-dd").format(
                new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L));
        int streak;
        if (yesterday.equals(session.getLastCheckin())) {
            streak = session.getCheckinStreak() + 1;
        } else {
            streak = 1;
        }

        int goldReward = 50 + streak * 10;
        session.addGold(goldReward);
        session.setLastCheckin(today);
        session.setCheckinStreak(streak);

        if (streak % 7 == 0) {
            session.getInventory().addTool("fertilizer", 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 签到成功！\n");
        sb.append("💰 获得 ").append(goldReward).append(" 金币\n");
        sb.append("📅 连续签到 ").append(streak).append(" 天");
        if (streak % 7 == 0) {
            sb.append("\n🎁 连签奖励: 加速肥料 x1");
        }
        return CommandResult.success(sb.toString());
    }
}
