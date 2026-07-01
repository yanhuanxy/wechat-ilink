package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.mode.BotModeType;
import com.github.wechat.ilink.bot.mode.ModeOutcome;
import com.github.wechat.ilink.bot.session.PlayerSession;

/**
 * 不可变的事件负载。字段按事件按需填充（未用到者为 {@code null}/0）。
 * 经 {@link Builder} 构造，由触发点（{@code ModeRouter}/{@code GameBot} 等）组装。
 */
public final class HookContext {

    private final String userId;
    private final String text;
    private final PlayerSession session;
    private final BotModeType targetMode;
    private final BotModeType fromMode;
    private final BotModeType toMode;
    private final ModeOutcome outcome;
    private final Throwable throwable;
    private final long durationMs;
    private final String sendKind;

    private HookContext(Builder b) {
        this.userId = b.userId;
        this.text = b.text;
        this.session = b.session;
        this.targetMode = b.targetMode;
        this.fromMode = b.fromMode;
        this.toMode = b.toMode;
        this.outcome = b.outcome;
        this.throwable = b.throwable;
        this.durationMs = b.durationMs;
        this.sendKind = b.sendKind;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUserId() {
        return userId;
    }

    public String getText() {
        return text;
    }

    public PlayerSession getSession() {
        return session;
    }

    public BotModeType getTargetMode() {
        return targetMode;
    }

    public BotModeType getFromMode() {
        return fromMode;
    }

    public BotModeType getToMode() {
        return toMode;
    }

    public ModeOutcome getOutcome() {
        return outcome;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getSendKind() {
        return sendKind;
    }

    public static final class Builder {
        private String userId;
        private String text;
        private PlayerSession session;
        private BotModeType targetMode;
        private BotModeType fromMode;
        private BotModeType toMode;
        private ModeOutcome outcome;
        private Throwable throwable;
        private long durationMs;
        private String sendKind;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder session(PlayerSession session) {
            this.session = session;
            return this;
        }

        public Builder targetMode(BotModeType targetMode) {
            this.targetMode = targetMode;
            return this;
        }

        public Builder fromMode(BotModeType fromMode) {
            this.fromMode = fromMode;
            return this;
        }

        public Builder toMode(BotModeType toMode) {
            this.toMode = toMode;
            return this;
        }

        public Builder outcome(ModeOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder sendKind(String sendKind) {
            this.sendKind = sendKind;
            return this;
        }

        public HookContext build() {
            return new HookContext(this);
        }
    }
}
