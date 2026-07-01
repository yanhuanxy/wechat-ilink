package com.github.wechat.ilink.bot.mode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link ModeSender} 装饰器：对内容发送（文本/图片/文件/视频）做有限次指数退避重试，
 * 全部失败则记 ERROR 并放弃（不向上抛，保持既有 {@code sendSafe} "尽力发送" 语义）。
 *
 * 不重试：
 * <ul>
 *   <li>{@code startTyping/stopTyping} —— 幂等指示器，无内容负载。</li>
 *   <li>{@code sendTextWithTyping} —— SDK 实现内含 typing 延时 sleep，重试会在消息线程上累积阻塞。</li>
 * </ul>
 */
public class RetrySender implements ModeSender {

    private static final Logger log = LoggerFactory.getLogger(RetrySender.class);
    private static final long MAX_BACKOFF_MS = 4_000L;

    private final ModeSender delegate;
    private final int maxAttempts;
    private final long backoffMs;

    public RetrySender(ModeSender delegate, int maxAttempts, long backoffMs) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0L, backoffMs);
    }

    @Override
    public void sendText(final String userId, final String text) throws IOException {
        retry("sendText", new IoAction() {
            @Override public void run() throws IOException { delegate.sendText(userId, text); }
        });
    }

    @Override
    public void sendImage(final String userId, final byte[] imageBytes, final String fileName, final String caption)
            throws IOException {
        retry("sendImage", new IoAction() {
            @Override public void run() throws IOException { delegate.sendImage(userId, imageBytes, fileName, caption); }
        });
    }

    @Override
    public void sendFile(final String userId, final byte[] fileBytes, final String fileName, final String caption)
            throws IOException {
        retry("sendFile", new IoAction() {
            @Override public void run() throws IOException { delegate.sendFile(userId, fileBytes, fileName, caption); }
        });
    }

    @Override
    public void sendVideo(final String userId, final byte[] videoBytes, final String fileName,
                          final Integer playLengthMs, final String caption) throws IOException {
        retry("sendVideo", new IoAction() {
            @Override public void run() throws IOException {
                delegate.sendVideo(userId, videoBytes, fileName, playLengthMs, caption);
            }
        });
    }

    @Override
    public void sendTextWithTyping(String userId, String text, long typingMillis) throws IOException {
        delegate.sendTextWithTyping(userId, text, typingMillis);
    }

    @Override
    public void startTyping(String userId) throws IOException {
        delegate.startTyping(userId);
    }

    @Override
    public void stopTyping(String userId) throws IOException {
        delegate.stopTyping(userId);
    }

    private void retry(String op, IoAction action) {
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (IOException e) {
                last = e;
                if (attempt < maxAttempts) {
                    long delay = Math.min(backoffMs * (1L << (attempt - 1)), MAX_BACKOFF_MS);
                    log.warn("发送失败将重试, op={}, attempt={}/{}, delayMs={}, err={}",
                            op, attempt, maxAttempts, delay, e.toString());
                    sleep(delay);
                }
            }
        }
        log.error("发送最终失败（已尝试{}次）, op={}", maxAttempts, op, last);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
