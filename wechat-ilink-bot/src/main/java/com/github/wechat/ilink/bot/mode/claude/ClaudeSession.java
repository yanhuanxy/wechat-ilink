package com.github.wechat.ilink.bot.mode.claude;

public final class ClaudeSession {

    private final String sessionId;
    private final String userId;
    private final String cwd;
    private final String model;
    private final String title;
    private final long createdAt;
    private final long updatedAt;

    public ClaudeSession(String sessionId, String userId, String cwd, String model,
                         String title, long createdAt, long updatedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.cwd = cwd;
        this.model = model;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getCwd() {
        return cwd;
    }

    public String getModel() {
        return model;
    }

    public String getTitle() {
        return title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
