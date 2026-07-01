package com.github.wechat.ilink.bot.command;

import java.util.Collections;
import java.util.Map;

public class CommandResult {

    public static final String IMAGE_DATA_KEY = "qrCodeUrl";

    private final boolean success;
    private final String message;
    private final Map<String, Object> data;

    private CommandResult(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data != null ? data : Collections.<String, Object>emptyMap();
    }

    public static CommandResult success(String message) {
        return new CommandResult(true, message, null);
    }

    public static CommandResult success(String message, Map<String, Object> data) {
        return new CommandResult(true, message, data);
    }

    public static CommandResult error(String message) {
        return new CommandResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
