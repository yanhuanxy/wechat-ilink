package com.github.wechat.ilink.bot.mode;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * per-user 固定窗口限流：窗口内允许 {@code maxPerWindow} 次 {@link #tryAcquire}，超出拒绝；
 * 窗口（{@code windowMs}）滚动后计数清零。后台 daemon 线程清理过期窗口，防内存堆积。
 *
 * 用途：在 {@code ModeRouter.route} 入口防止单用户刷消息打满 MCP/LLM/命令处理。
 */
public class RateLimiter {

    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<String, Window>();
    private final long windowMs;
    private final int maxPerWindow;
    private ScheduledExecutorService cleanupExecutor;

    public RateLimiter(int maxPerWindow, long windowMs) {
        this.maxPerWindow = maxPerWindow;
        this.windowMs = windowMs;
    }

    /** 命中则返回 true（放行）；窗口内超过上限返回 false（拒绝）。userId 空/白视为放行。 */
    public boolean tryAcquire(String userId) {
        if (userId == null || userId.isEmpty()) {
            return true;
        }
        Window w = windows.computeIfAbsent(userId, new java.util.function.Function<String, Window>() {
            @Override
            public Window apply(String k) {
                return new Window(System.currentTimeMillis());
            }
        });
        synchronized (w) {
            long now = System.currentTimeMillis();
            if (now - w.start >= windowMs) {
                w.start = now;
                w.count = 0;
            }
            w.count++;
            return w.count <= maxPerWindow;
        }
    }

    public int size() {
        return windows.size();
    }

    public void startCleanup() {
        if (cleanupExecutor != null) {
            return;
        }
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "rate-limiter-cleanup");
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
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> e = it.next();
            if (now - e.getValue().start >= windowMs) {
                it.remove();
            }
        }
    }

    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
        windows.clear();
    }

    static class Window {
        long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 0;
        }
    }
}
