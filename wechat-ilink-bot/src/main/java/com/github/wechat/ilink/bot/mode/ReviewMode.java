package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.task.TaskMessageHandler;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;

public class ReviewMode {

    private final TaskMessageHandler taskHandler;

    public ReviewMode(TaskMessageHandler taskHandler) {
        this.taskHandler = taskHandler;
    }

    public boolean isEnabled() {
        return taskHandler != null;
    }

    public ModeOutcome handleVideo(ModeContext ctx, PlayerSession session, WeixinMessage msg) {
        if (taskHandler == null) {
            return ModeOutcome.notMatched();
        }
        boolean handled = taskHandler.tryHandleVideo(session.getUserId(), msg);
        return handled ? ModeOutcome.handled() : ModeOutcome.notMatched();
    }

    public ModeOutcome handlePendingPrompt(ModeContext ctx, PlayerSession session, String text) {
        if (taskHandler == null) {
            return ModeOutcome.notMatched();
        }
        boolean handled = taskHandler.tryHandleTaskText(session.getUserId(), text);
        return handled ? ModeOutcome.handled() : ModeOutcome.notMatched();
    }
}
