package com.github.wechat.ilink.bot.mode.claude;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Claude Bridge 入向文件缓冲：用户先发图片/文件 → 缓冲票据 → 60 秒内的下一条文字触发消费。
 * 仿 {@code task.VideoTaskBuffer}，per-user 单票据，超时由后台线程清理。SDK-free。
 */
public class BridgeFileBuffer {

    private static final Logger log = LoggerFactory.getLogger(BridgeFileBuffer.class);
    private static final long CLEANUP_INTERVAL_MS = 30_000L;

    private final ConcurrentHashMap<String, FileTicket> pending = new ConcurrentHashMap<String, FileTicket>();
    private final long ttlMs;
    private final long maxBytes;
    private ScheduledExecutorService cleanupExecutor;

    public BridgeFileBuffer(long ttlMs, long maxBytes) {
        this.ttlMs = ttlMs;
        this.maxBytes = maxBytes;
    }

    public PutResult put(String userId, byte[] bytes, String fileName, boolean image) {
        if (userId == null || userId.isEmpty()) {
            return PutResult.rejected("userId 为空");
        }
        if (bytes == null || bytes.length == 0) {
            return PutResult.rejected("文件内容为空");
        }
        if (bytes.length > maxBytes) {
            return PutResult.rejected("文件过大: " + bytes.length + " bytes，上限 " + maxBytes);
        }
        pending.put(userId, new FileTicket(bytes, fileName, image, System.currentTimeMillis()));
        log.info("入向文件已缓冲, userId={}, size={}KB, image={}, fileName={}",
                userId, bytes.length / 1024, image, fileName);
        return PutResult.accepted();
    }

    public FileTicket consume(String userId) {
        if (userId == null) {
            return null;
        }
        return pending.remove(userId);
    }

    public int size() {
        return pending.size();
    }

    public void startCleanup() {
        if (cleanupExecutor != null) {
            return;
        }
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "bridge-file-buffer-cleanup");
                t.setDaemon(true);
                return t;
            }
        });
        cleanupExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                purgeExpired();
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, FileTicket>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FileTicket> entry = it.next();
            if (now - entry.getValue().getReceivedAt() > ttlMs) {
                it.remove();
                log.info("过期入向文件票据已清理, userId={}", entry.getKey());
            }
        }
    }

    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
        pending.clear();
    }

    public static class FileTicket {
        private final byte[] bytes;
        private final String fileName;
        private final boolean image;
        private final long receivedAt;

        FileTicket(byte[] bytes, String fileName, boolean image, long receivedAt) {
            this.bytes = bytes;
            this.fileName = fileName;
            this.image = image;
            this.receivedAt = receivedAt;
        }

        public byte[] getBytes() { return bytes; }
        public String getFileName() { return fileName; }
        public boolean isImage() { return image; }
        public long getReceivedAt() { return receivedAt; }
    }

    public static class PutResult {
        private final boolean accepted;
        private final String errorMessage;

        private PutResult(boolean accepted, String errorMessage) {
            this.accepted = accepted;
            this.errorMessage = errorMessage;
        }

        static PutResult accepted() {
            return new PutResult(true, null);
        }

        static PutResult rejected(String reason) {
            return new PutResult(false, reason);
        }

        public boolean isAccepted() { return accepted; }
        public String getErrorMessage() { return errorMessage; }
    }
}
