package com.github.wechat.ilink.bot.llm;

import java.util.List;

public interface LlmProvider {

    String chat(List<ChatMessage> messages);

    default void chatStream(List<ChatMessage> messages, StreamCallback callback) {
        try {
            String result = chat(messages);
            if (result != null) {
                callback.onComplete(result);
            } else {
                callback.onError(new RuntimeException("LLM returned null"));
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
