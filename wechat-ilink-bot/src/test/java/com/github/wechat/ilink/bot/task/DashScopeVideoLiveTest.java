package com.github.wechat.ilink.bot.task;

import com.github.wechat.ilink.bot.config.TaskConfig;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端跑通：上传真实 mp4 到 DashScope → 调用 qwen3.5-omni-plus → 拿到点评。
 *
 * 触发方式：
 *   set DASHSCOPE_VIDEO_LIVE=true
 *   mvn test -Dtest=DashScopeVideoLiveTest
 *
 * 默认使用 data/task-config.json 配置 + data/tasks/ 下最近的一个 .mp4 样本。
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_VIDEO_LIVE", matches = "true")
class DashScopeVideoLiveTest {

    private static final String CONFIG_PATH = "../data/task-config.json";
    private static final String DEFAULT_VIDEO_DIR = "../data/tasks";
    private static final String LIVE_CLAUDE_HOME = "../data/claude-home";

    @Test
    void execute_realVideo_returnsNonEmptyReview() throws Exception {
        TaskConfig config = TaskConfig.load(CONFIG_PATH);
        assertTrue(config.isEnabled(), "task-config.json enabled 必须为 true");
        config.setClaudeHome(LIVE_CLAUDE_HOME);

        byte[] videoBytes = loadSampleVideo();
        assertTrue(videoBytes.length > 0, "测试样本视频不能为空");

        DashScopeVideoProvider provider = new DashScopeVideoProvider(config);
        TaskRequest req = new TaskRequest("live-test-user", videoBytes, "sample.mp4",
                "测试点评，关注手型即可", null);

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
        assertTrue(latch.await(240, TimeUnit.SECONDS), "任务未在 240s 内完成");

        if (err.get() != null) {
            throw new AssertionError("DashScope 视频任务失败: " + err.get().getMessage(), err.get());
        }
        assertNotNull(got.get());
        assertFalse(got.get().trim().isEmpty(), "点评内容不应为空");
        System.out.println("==== 点评结果 ====");
        System.out.println(got.get());
    }

    private byte[] loadSampleVideo() throws Exception {
        Path tasksDir = Paths.get(DEFAULT_VIDEO_DIR);
        assertTrue(Files.exists(tasksDir), "样本目录不存在: " + tasksDir.toAbsolutePath());

        Path sample = Files.walk(tasksDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".mp4"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未在 " + tasksDir + " 下找到 .mp4 样本"));

        System.out.println("[LIVE] 使用样本视频: " + sample);
        return Files.readAllBytes(sample);
    }
}
