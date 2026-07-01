package com.github.wechat.ilink.bot.llm;

public interface StreamCallback {

    void onToken(String token);

    void onComplete(String fullResponse);

    void onError(Throwable t);
}
