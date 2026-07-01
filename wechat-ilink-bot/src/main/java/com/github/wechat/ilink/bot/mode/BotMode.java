package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.session.PlayerSession;

public interface BotMode {

    BotModeType type();

    ModeOutcome handleText(ModeContext ctx, PlayerSession session, String text);
}
