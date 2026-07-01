package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.persistence.ActionRankRepository;
import com.github.wechat.ilink.bot.session.PlayerSession;

import java.util.Map;

public class WaterRankCommand implements Command {

    private final ActionRankRepository rankRepo;

    public WaterRankCommand(ActionRankRepository rankRepo) {
        this.rankRepo = rankRepo;
    }

    @Override
    public String name() { return "WATER_RANK"; }

    @Override
    public String description() { return "浇水排行"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        Map<String, Integer> ranking = rankRepo.getTopScores("WATER", 10);
        StringBuilder sb = new StringBuilder();
        sb.append("💧 浇水排行榜\n");
        if (ranking.isEmpty()) {
            sb.append("暂无数据");
        } else {
            int rank = 1;
            for (Map.Entry<String, Integer> entry : ranking.entrySet()) {
                sb.append(rank++).append(". ").append(entry.getKey())
                        .append(" - ").append(entry.getValue()).append("分\n");
            }
        }
        return CommandResult.success(sb.toString().trim());
    }
}
