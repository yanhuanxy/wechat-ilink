package com.github.wechat.ilink.sdk.core.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import com.github.wechat.ilink.sdk.core.listener.ListenerRegistry;
import com.github.wechat.ilink.sdk.core.listener.OnHeartbeatListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 特征化测试：心跳监听器逐个隔离——一个监听器抛异常不中断其余监听器，也不把 success 误报成 failure。 */
class HeartbeatServiceTest {

  private static final class RecordingListener implements OnHeartbeatListener {
    final AtomicInteger successes = new AtomicInteger();
    final AtomicInteger failures = new AtomicInteger();

    @Override
    public void onHeartbeatSuccess() {
      successes.incrementAndGet();
    }

    @Override
    public void onHeartbeatFailure(Throwable e) {
      failures.incrementAndGet();
    }
  }

  private static final class ThrowingListener implements OnHeartbeatListener {
    @Override
    public void onHeartbeatSuccess() {
      throw new IllegalStateException("boom on success");
    }

    @Override
    public void onHeartbeatFailure(Throwable e) {
      throw new IllegalStateException("boom on failure");
    }
  }

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  @AfterEach
  void tearDown() {
    scheduler.shutdownNow();
  }

  private static void awaitAtLeast(AtomicInteger counter, int n) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 2000L;
    while (counter.get() < n && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L);
    }
  }

  @Test
  void throwingListenerDoesNotAbortOthersNorMisreportsFailure() throws Exception {
    ListenerRegistry registry = new ListenerRegistry();
    RecordingListener recording = new RecordingListener();
    registry.addOnHeartbeatListener(new ThrowingListener()); // 注册在前，先被调用
    registry.addOnHeartbeatListener(recording);

    HeartbeatService service = new HeartbeatService(scheduler, 20L, () -> {}, registry);
    try {
      service.start();
      awaitAtLeast(recording.successes, 2);
      assertTrue(recording.successes.get() >= 2, "later listener starved by throwing listener");
      assertEquals(0, recording.failures.get(), "success must not be misreported as failure");
    } finally {
      service.close();
    }
  }

  @Test
  void throwingFailureListenerDoesNotAbortOtherFailureNotifications() throws Exception {
    ListenerRegistry registry = new ListenerRegistry();
    RecordingListener recording = new RecordingListener();
    registry.addOnHeartbeatListener(new ThrowingListener());
    registry.addOnHeartbeatListener(recording);

    HeartbeatService service =
        new HeartbeatService(
            scheduler,
            20L,
            () -> {
              throw new IllegalStateException("liveness stale");
            },
            registry);
    try {
      service.start();
      awaitAtLeast(recording.failures, 2);
      assertTrue(recording.failures.get() >= 2, "later listener starved on failure path");
      assertEquals(0, recording.successes.get());
    } finally {
      service.close();
    }
  }
}
