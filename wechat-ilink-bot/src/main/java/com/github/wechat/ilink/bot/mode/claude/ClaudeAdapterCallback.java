package com.github.wechat.ilink.bot.mode.claude;

public interface ClaudeAdapterCallback {

    void onSessionId(String sessionId);

    void onToken(String token);

    void onComplete(String fullResponse);

    void onError(Throwable t);
}
