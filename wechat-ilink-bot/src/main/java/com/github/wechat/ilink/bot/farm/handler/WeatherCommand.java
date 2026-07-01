package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.farm.model.Weather;
import com.github.wechat.ilink.bot.session.PlayerSession;

public class WeatherCommand implements Command {

    @Override
    public String name() { return "WEATHER"; }

    @Override
    public String description() { return "天气查询"; }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        Weather weather = Weather.today();
        StringBuilder sb = new StringBuilder();
        sb.append("🌤️ 今日天气: ").append(weather.getName()).append("\n");
        sb.append("效果: ").append(weather.getEffect());
        return CommandResult.success(sb.toString());
    }
}
