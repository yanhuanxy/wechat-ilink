package com.github.wechat.ilink.bot.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 会话刷盘调度器：合并突发写（per-user debounce）+ 周期兜底 flush。
 *
 * 实际落盘委托 {@link SessionManager#flushNow} / {@link SessionManager#flushAllDirty}，
 * 两者均在每用户锁内执行，保证读到一致快照（异步 flush 不会撞上正在变更的命令）。
 *
 * 模式：
 * <ul>
 *   <li>{@code flushDelayMs <= 0} —— {@link #scheduleFlush} 同步立即刷（默认，保持"命令返回即落盘"语义）。</li>
 *   <li>{@code flushDelayMs > 0} —— per-user debounce：短窗口内的多次写合并为一次落盘。</li>
 * </ul>
 * {@code flushIntervalMs > 0} 时启动周期兜底，定期刷盘所有 dirty 会话（崩溃恢复用）。
 */
public class FlushGate {

    private static final Logger log = LoggerFactory.getLogger(FlushGate.class);

    private final SessionManager sessions;
    private final long flushDelayMs;
    private final long flushIntervalMs;
    private final Set<String> pending = new HashSet<String>();
    private ScheduledExecutorService executor;

    public FlushGate(SessionManager sessions, long flushDelayMs, long flushIntervalMs) {
        this.sessions = sessions;
        this.flushDelayMs = flushDelayMs;
        this.flushIntervalMs = flushIntervalMs;
    }

    public void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "session-flush-gate");
                t.setDaemon(true);
                return t;
            }
        });
        if (flushIntervalMs > 0) {
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    sessions.flushAllDirty();
                }
            }, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 标记某用户需刷盘：{@code flushDelayMs <= 0} 同步立即刷；否则 debounce
     * （该用户已有挂起 flush 则跳过，合并到那次）。
     */
    public void scheduleFlush(final String userId) {
        if (flushDelayMs <= 0) {
            sessions.flushNow(userId);
            return;
        }
        synchronized (pending) {
            if (!pending.add(userId)) {
                return;
            }
        }
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    sessions.flushNow(userId);
                } catch (Exception e) {
                    log.error("delayed flush failed, userId={}", userId, e);
                } finally {
                    synchronized (pending) {
                        pending.remove(userId);
                    }
                }
            }
        }, flushDelayMs, TimeUnit.MILLISECONDS);
    }

    /** 立即全量刷盘（shutdown 用）。 */
    public void flushAllNow() {
        sessions.flushAllDirty();
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
