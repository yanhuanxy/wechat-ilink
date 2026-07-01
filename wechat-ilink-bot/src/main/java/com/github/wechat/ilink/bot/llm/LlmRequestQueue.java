package com.github.wechat.ilink.bot.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LlmRequestQueue {

    private static final Logger log = LoggerFactory.getLogger(LlmRequestQueue.class);

    private final ThreadPoolExecutor executor;
    private final Set<String> activeUsers;

    public LlmRequestQueue(int corePoolSize, int maxQueueSize) {
        this.activeUsers = ConcurrentHashMap.newKeySet();
        this.executor = new ThreadPoolExecutor(
                corePoolSize, corePoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxQueueSize),
                new LlmThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public boolean submit(String userId, Runnable task) {
        if (!activeUsers.add(userId)) {
            log.warn("用户 {} 已有正在处理的 LLM 请求，跳过", userId);
            return false;
        }
        try {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        task.run();
                    } finally {
                        activeUsers.remove(userId);
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            activeUsers.remove(userId);
            log.warn("LLM 队列已满，拒绝用户 {} 的请求", userId);
            return false;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
        log.info("LLM 请求队列已关闭");
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    private static class LlmThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "llm-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
