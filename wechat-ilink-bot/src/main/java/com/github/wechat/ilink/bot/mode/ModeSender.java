package com.github.wechat.ilink.bot.mode;

import java.io.IOException;

public interface ModeSender {

    void sendText(String userId, String text) throws IOException;

    void sendTextWithTyping(String userId, String text, long typingMillis) throws IOException;

    void sendImage(String userId, byte[] imageBytes, String fileName, String caption) throws IOException;

    void sendFile(String userId, byte[] fileBytes, String fileName, String caption) throws IOException;

    void sendVideo(String userId, byte[] videoBytes, String fileName, Integer playLengthMs, String caption)
            throws IOException;

    void startTyping(String userId) throws IOException;

    void stopTyping(String userId) throws IOException;
}
