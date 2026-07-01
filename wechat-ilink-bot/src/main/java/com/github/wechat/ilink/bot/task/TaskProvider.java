package com.github.wechat.ilink.bot.task;

import com.github.wechat.ilink.bot.llm.StreamCallback;

public interface TaskProvider {

    void execute(TaskRequest request, StreamCallback callback);
}
