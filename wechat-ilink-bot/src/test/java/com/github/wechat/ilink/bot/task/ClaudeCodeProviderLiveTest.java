package com.github.wechat.ilink.bot.task;

import com.github.wechat.ilink.bot.config.TaskConfig;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仅当本机已安装并登录 claude CLI 时运行：设置环境变量 CLAUDE_CODE_LIVE=true 触发。
 * 无 claude CLI 时自动跳过。
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_CODE_LIVE", matches = "true")
class ClaudeCodeProviderLiveTest {

    @TempDir
    File tempDir;

    @Test
    void execute_simplePrompt_returnsNonEmpty() throws Exception {
        TaskConfig config = new TaskConfig();
        config.setEnabled(true);
        config.setWorkspaceRoot(tempDir.getAbsolutePath());
        config.setTimeoutMs(120_000L);
        config.setPermissionMode("acceptEdits");

        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);
        TaskRequest req = new TaskRequest("live-user", new byte[]{1}, "input.mp4",
                "请回复一句中文：hello from claude code", null);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> got = new AtomicReference<String>(null);
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);
        StreamCallback cb = new StreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(String fullResponse) {
                got.set(fullResponse); latch.countDown();
            }
            @Override public void onError(Throwable t) { err.set(t); latch.countDown(); }
        };

        provider.execute(req, cb);
        assertTrue(latch.await(150, TimeUnit.SECONDS));

        if (err.get() != null) {
            throw new AssertionError("claude code failed: " + err.get().getMessage(), err.get());
        }
        assertTrue(got.get() != null && !got.get().isEmpty(),
                "claude code should return non-empty output");
    }

    @Test
    void execute_writesVideoFileToWorkspace() throws Exception {
        TaskConfig config = new TaskConfig();
        config.setEnabled(true);
        config.setWorkspaceRoot(tempDir.getAbsolutePath());
        config.setTimeoutMs(120_000L);

        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);
        byte[] videoBytes = "fake-video-content".getBytes();
        TaskRequest req = new TaskRequest("live-user-2", videoBytes, "input.mp4",
                "回复 ok 即可，不用看视频", null);

        final CountDownLatch latch = new CountDownLatch(1);
        StreamCallback cb = new StreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(String fullResponse) { latch.countDown(); }
            @Override public void onError(Throwable t) { latch.countDown(); }
        };

        provider.execute(req, cb);
        assertTrue(latch.await(150, TimeUnit.SECONDS));

        Path taskDir = findTaskDir(tempDir.toPath().resolve("live-user-2"));
        assertTrue(Files.exists(taskDir.resolve("input.mp4")));
    }

    private Path findTaskDir(Path userDir) throws Exception {
        return Files.list(userDir).findFirst().orElseThrow(() ->
                new AssertionError("no task dir under " + userDir));
    }
}
