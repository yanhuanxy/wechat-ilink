package com.github.wechat.ilink.bot.mode;

public enum BotModeType {
    CHAT,
    CLAUDE,
    FARM,
    REVIEW,
    AUTOGAME;

    public static BotModeType defaultMode() {
        return CHAT;
    }

    public static BotModeType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String upper = name.trim().toUpperCase();
        for (BotModeType type : values()) {
            if (type.name().equals(upper)) {
                return type;
            }
        }
        return null;
    }
}
