package com.github.wechat.ilink.bot.llm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LlmRequestQueueTest {

    private LlmRequestQueue queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.shutdown();
        }
    }

    @Test
    void submit_singleTask_executesSuccessfully() throws Exception {
        queue = new LlmRequestQueue(1, 10);
        AtomicBoolean executed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        boolean accepted = queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                executed.set(true);
                latch.countDown();
            }
        });

        assertTrue(accepted);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(executed.get());
    }

    @Test
    void submit_sameUserTwice_secondRejected() throws Exception {
        queue = new LlmRequestQueue(1, 10);
        CountDownLatch blockingLatch = new CountDownLatch(1);
        CountDownLatch secondAttempt = new CountDownLatch(1);

        queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                try {
                    blockingLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread.sleep(100);
        boolean accepted = queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                secondAttempt.countDown();
            }
        });

        assertFalse(accepted);
        blockingLatch.countDown();
    }

    @Test
    void submit_differentUsers_bothExecuted() throws Exception {
        queue = new LlmRequestQueue(2, 10);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean user1Executed = new AtomicBoolean(false);
        AtomicBoolean user2Executed = new AtomicBoolean(false);

        queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                user1Executed.set(true);
                latch.countDown();
            }
        });
        queue.submit("user2", new Runnable() {
            @Override
            public void run() {
                user2Executed.set(true);
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(user1Executed.get());
        assertTrue(user2Executed.get());
    }

    @Test
    void submit_queueFull_rejectsTask() throws Exception {
        queue = new LlmRequestQueue(1, 1);
        CountDownLatch blockingLatch = new CountDownLatch(1);

        queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                try {
                    blockingLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread.sleep(100);

        queue.submit("user2", new Runnable() {
            @Override
            public void run() {
            }
        });

        boolean accepted = queue.submit("user3", new Runnable() {
            @Override
            public void run() {
            }
        });

        assertFalse(accepted);
        blockingLatch.countDown();
    }

    @Test
    void submit_taskCompletes_userRemovedFromActive() throws Exception {
        queue = new LlmRequestQueue(1, 10);
        CountDownLatch firstLatch = new CountDownLatch(1);
        CountDownLatch secondLatch = new CountDownLatch(1);

        queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                firstLatch.countDown();
            }
        });

        assertTrue(firstLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);

        boolean accepted = queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                secondLatch.countDown();
            }
        });

        assertTrue(accepted);
        assertTrue(secondLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void shutdown_noNewTasksAccepted() throws Exception {
        queue = new LlmRequestQueue(1, 10);

        queue.shutdown();

        boolean accepted = queue.submit("user1", new Runnable() {
            @Override
            public void run() {
            }
        });

        assertFalse(accepted);
    }

    @Test
    void submit_taskThrows_userStillRemovedFromActive() throws Exception {
        queue = new LlmRequestQueue(1, 10);
        CountDownLatch firstLatch = new CountDownLatch(1);

        queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                firstLatch.countDown();
                throw new RuntimeException("test error");
            }
        });

        assertTrue(firstLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);

        AtomicBoolean secondExecuted = new AtomicBoolean(false);
        CountDownLatch secondLatch = new CountDownLatch(1);

        boolean accepted = queue.submit("user1", new Runnable() {
            @Override
            public void run() {
                secondExecuted.set(true);
                secondLatch.countDown();
            }
        });

        assertTrue(accepted);
        assertTrue(secondLatch.await(2, TimeUnit.SECONDS));
        assertTrue(secondExecuted.get());
    }
}
