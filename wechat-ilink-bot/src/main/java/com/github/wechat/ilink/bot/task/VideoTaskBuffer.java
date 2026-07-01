package com.github.wechat.ilink.bot.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VideoTaskBuffer {

    private static final Logger log = LoggerFactory.getLogger(VideoTaskBuffer.class);
    private static final long CLEANUP_INTERVAL_MS = 30_000L;

    private final ConcurrentHashMap<String, VideoTicket> pending = new ConcurrentHashMap<String, VideoTicket>();
    private final long ttlMs;
    private final long maxVideoBytes;
    private ScheduledExecutorService cleanupExecutor;

    public VideoTaskBuffer(long ttlMs, long maxVideoBytes) {
        this.ttlMs = ttlMs;
        this.maxVideoBytes = maxVideoBytes;
    }

    public PutResult put(String userId, byte[] videoBytes, String fileName) {
        if (userId == null || userId.isEmpty()) {
            return PutResult.rejected("userId 为空");
        }
        if (videoBytes == null || videoBytes.length == 0) {
            return PutResult.rejected("视频内容为空");
        }
        if (videoBytes.length > maxVideoBytes) {
            return PutResult.rejected("视频过大: " + videoBytes.length + " bytes，上限 " + maxVideoBytes);
        }
        pending.put(userId, new VideoTicket(videoBytes, fileName, System.currentTimeMillis()));
        log.info("视频已缓冲, userId={}, size={}KB, fileName={}",
                userId, videoBytes.length / 1024, fileName);
        return PutResult.accepted();
    }

    public VideoTicket consume(String userId) {
        if (userId == null) {
            return null;
        }
        return pending.remove(userId);
    }

    /** 是否存在该用户的待处理视频票据（不消费）。供诊断日志/门控使用。 */
    public boolean hasPending(String userId) {
        return userId != null && pending.containsKey(userId);
    }

    /** 取票据但不移除（支持 60s 窗口内多次 prompt 命中同一视频）；无则 null。票据靠 {@link #purgeExpired} 按 TTL 回收。 */
    public VideoTicket peek(String userId) {
        return userId == null ? null : pending.get(userId);
    }

    public int size() {
        return pending.size();
    }

    public void startCleanup() {
        if (cleanupExecutor != null) {
            return;
        }
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "video-buffer-cleanup");
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
        Iterator<Map.Entry<String, VideoTicket>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, VideoTicket> entry = it.next();
            if (now - entry.getValue().getReceivedAt() > ttlMs) {
                it.remove();
                log.info("过期视频票据已清理, userId={}", entry.getKey());
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

    public static class VideoTicket {
        private final byte[] videoBytes;
        private final String fileName;
        private final long receivedAt;

        VideoTicket(byte[] videoBytes, String fileName, long receivedAt) {
            this.videoBytes = videoBytes;
            this.fileName = fileName;
            this.receivedAt = receivedAt;
        }

        public byte[] getVideoBytes() { return videoBytes; }
        public String getFileName() { return fileName; }
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
