package com.github.wechat.ilink.bot.persistence;

/**
 * bot 登录会话的持久化记录（纯数据，无 SDK 依赖）。
 *
 * <p>对应 SDK 的 {@code LoginContext} 四要素 + 消息游标，用于服务重启后免扫码恢复连接。
 * SDK 类型的转换由应用层（{@code BotInstance}）完成，持久化层只见纯字符串，保持架构边界。</p>
 */
public class BotSessionRecord {

    private final String name;
    private final String botToken;
    private final String userId;
    private final String botId;
    private final String baseUrl;
    private final String updatesCursor;

    public BotSessionRecord(String name, String botToken, String userId,
                            String botId, String baseUrl, String updatesCursor) {
        this.name = name;
        this.botToken = botToken;
        this.userId = userId;
        this.botId = botId;
        this.baseUrl = baseUrl;
        this.updatesCursor = updatesCursor;
    }

    public String getName() {
        return name;
    }

    public String getBotToken() {
        return botToken;
    }

    public String getUserId() {
        return userId;
    }

    public String getBotId() {
        return botId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUpdatesCursor() {
        return updatesCursor;
    }
}
