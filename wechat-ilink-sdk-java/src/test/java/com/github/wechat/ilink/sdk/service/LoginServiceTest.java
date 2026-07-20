package com.github.wechat.ilink.sdk.service;

import static org.junit.jupiter.api.Assertions.*;

import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.http.HttpClientFacade;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.login.LoginStatus;
import com.github.wechat.ilink.sdk.core.retry.ExponentialBackoffStrategy;
import com.github.wechat.ilink.sdk.core.retry.RetryPolicy;
import com.github.wechat.ilink.sdk.core.serializer.JsonSerializer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 特征化测试：登录二维码状态轮询的间隔与取消行为（ADR-0001 后续修复）。 */
class LoginServiceTest {

  /** 假 facade：get_qrcode_status 永远返回 waiting，并计数请求次数。不发真实网络请求。 */
  private static final class CountingWaitingFacade extends HttpClientFacade {
    final AtomicInteger requests = new AtomicInteger();

    CountingWaitingFacade(ILinkConfig config) {
      super(config, new RetryPolicy(1, new ExponentialBackoffStrategy(1L, 1L, false)));
    }

    @Override
    public String get(String url, Map<String, String> headers) {
      requests.incrementAndGet();
      return "{\"status\":\"waiting\"}";
    }
  }

  private final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    pollingExecutor.shutdownNow();
  }

  @Test
  void pollingHonorsLoginPollIntervalInsteadOfHotLooping() throws Exception {
    ILinkConfig config =
        ILinkConfig.builder().loginPollIntervalMs(50L).loginTimeoutMs(60000L).build();
    CountingWaitingFacade facade = new CountingWaitingFacade(config);
    LoginService service = new LoginService(config, new JsonSerializer(), facade, pollingExecutor);

    service.startLoginPolling(
        "qr", new LoginStatus(), new AtomicReference<LoginContext>());
    Thread.sleep(400L);
    service.cancelCurrentLogin();

    int count = facade.requests.get();
    assertTrue(count >= 2, "should keep polling, got " + count);
    // 无间隔的热循环 400ms 内会打出成百上千次请求；50ms 间隔下理论上限 400/50=8，留余量。
    assertTrue(count <= 12, "polling looks like a hot loop: " + count + " requests in 400ms");
  }

  @Test
  void cancelCurrentLoginActuallyStopsThePollingLoop() throws Exception {
    ILinkConfig config =
        ILinkConfig.builder().loginPollIntervalMs(10L).loginTimeoutMs(60000L).build();
    CountingWaitingFacade facade = new CountingWaitingFacade(config);
    LoginService service = new LoginService(config, new JsonSerializer(), facade, pollingExecutor);

    CompletableFuture<LoginContext> future =
        service.startLoginPolling("qr", new LoginStatus(), new AtomicReference<LoginContext>());
    // 等循环真正跑起来再取消
    long deadline = System.currentTimeMillis() + 2000L;
    while (facade.requests.get() < 1 && System.currentTimeMillis() < deadline) {
      Thread.sleep(5L);
    }
    assertTrue(facade.requests.get() >= 1, "polling never started");

    service.cancelCurrentLogin();
    Thread.sleep(100L); // 允许正在 sleep 的一轮醒来退出
    int after = facade.requests.get();
    Thread.sleep(200L);
    assertTrue(
        facade.requests.get() <= after + 1,
        "polling kept running after cancel: " + after + " -> " + facade.requests.get());
    assertTrue(future.isDone(), "future should be settled after cancel");
  }
}
