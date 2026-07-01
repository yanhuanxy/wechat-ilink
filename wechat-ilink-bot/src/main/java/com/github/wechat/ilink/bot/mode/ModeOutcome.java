package com.github.wechat.ilink.bot.mode;

public final class ModeOutcome {

    public enum Status {
        HANDLED,
        NOT_MATCHED,
        SKIP
    }

    private final Status status;
    private final String errorMessage;

    private ModeOutcome(Status status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static ModeOutcome handled() {
        return new ModeOutcome(Status.HANDLED, null);
    }

    public static ModeOutcome notMatched() {
        return new ModeOutcome(Status.NOT_MATCHED, null);
    }

    public static ModeOutcome skip() {
        return new ModeOutcome(Status.SKIP, null);
    }

    public static ModeOutcome error(String message) {
        return new ModeOutcome(Status.HANDLED, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isHandled() {
        return status == Status.HANDLED;
    }
}
