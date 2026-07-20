package com.github.wechat.ilink.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.listener.OnHeartbeatListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 特征化测试：liveness 看门狗在"登录后消费方从未成功 poll"时也要报警（登录即武装基线，
 * 修复 baseline==0 永远沉默的盲区）。不发真实网络请求——checkLiveness 只比对时间戳。
 */
class ILinkClientLivenessWatchdogTest {

  @Test
  void watchdogFiresWhenConsumerNeverPolls() throws Exception {
    ILinkConfig config =
        ILinkConfig.builder().heartbeatIntervalMs(50L).livenessThresholdMs(100L).build();
    AtomicInteger failures = new AtomicInteger();
    AtomicReference<Throwable> lastFailure = new AtomicReference<Throwable>();
    OnHeartbeatListener listener =
        new OnHeartbeatListener() {
          @Override
          public void onHeartbeatSuccess() {}

          @Override
          public void onHeartbeatFailure(Throwable e) {
            failures.incrementAndGet();
            lastFailure.set(e);
          }
        };

    // resume 路径直接进入已登录态并启动心跳；之后刻意不跑接收循环。
    try (ILinkClient client =
        ILinkClient.builder()
            .config(config)
            .loginContext(new LoginContext("token", "user", "bot", "http://127.0.0.1:1"))
            .onHeartbeat(listener)
            .build()) {
      assertTrue(client.isLoggedIn());
      long deadline = System.currentTimeMillis() + 3000L;
      while (failures.get() < 1 && System.currentTimeMillis() < deadline) {
        Thread.sleep(20L);
      }
      assertTrue(failures.get() >= 1, "watchdog stayed silent although consumer never polled");
      assertTrue(
          String.valueOf(lastFailure.get().getMessage()).contains("liveness"),
          "unexpected failure cause: " + lastFailure.get());
    }
  }
}
