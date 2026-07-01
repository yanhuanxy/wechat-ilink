package com.github.wechat.ilink.bot.task;

import com.github.wechat.ilink.bot.config.TaskConfig;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeCodeProviderTest {

    @TempDir
    File tempDir;

    @Test
    void buildPrompt_withUserPrompt_includesVideoFileName() {
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);

        String prompt = provider.buildPrompt("分析这段视频", java.nio.file.Paths.get("input.mp4"));

        assertTrue(prompt.contains("分析这段视频"));
        assertTrue(prompt.contains("input.mp4"));
    }

    @Test
    void buildPrompt_emptyUserPrompt_onlyVideoHint() {
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);

        String prompt = provider.buildPrompt("", java.nio.file.Paths.get("clip.mp4"));

        assertFalse(prompt.contains("null"));
        assertTrue(prompt.contains("clip.mp4"));
    }

    @Test
    void execute_success_callsOnComplete() throws Exception {
        final FakeProcess fake = new FakeProcess(
                new ByteArrayInputStream("{\"result\":\"hello world\"}\n".getBytes()),
                new ByteArrayInputStream(new byte[0]),
                0);
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config) {
            @Override
            protected Process startProcess(String prompt, Path claudeHome, Path taskDir) {
                return fake;
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> got = new AtomicReference<String>(null);
        StreamCallback cb = new StreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(String fullResponse) {
                got.set(fullResponse); latch.countDown();
            }
            @Override public void onError(Throwable t) { latch.countDown(); }
        };

        provider.execute(new TaskRequest("user1", new byte[]{1, 2}, "x.mp4", "hi", null), cb);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertNotNull(got.get());
        assertTrue(got.get().contains("hello world"));
    }

    @Test
    void execute_nonZeroExit_callsOnError() throws Exception {
        final FakeProcess fake = new FakeProcess(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayInputStream("claude not found".getBytes()),
                127);
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config) {
            @Override
            protected Process startProcess(String prompt, Path claudeHome, Path taskDir) {
                return fake;
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);
        StreamCallback cb = new StreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(String fullResponse) { latch.countDown(); }
            @Override public void onError(Throwable t) { err.set(t); latch.countDown(); }
        };

        provider.execute(new TaskRequest("user1", new byte[]{1}, "x.mp4", "hi", null), cb);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertNotNull(err.get());
        assertTrue(err.get().getMessage().contains("127"));
    }

    @Test
    void extractText_contentBlockDelta_returnsText() throws Exception {
        InputStream in = new ByteArrayInputStream(
                "{\"content_block_delta\":{\"text\":\"片段1\"}}\n".getBytes());
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in));
        String line = reader.readLine();
        ClaudeCodeProvider.StreamConsumer consumer = newConsumer(new ByteArrayInputStream(new byte[0]), true);

        String text = consumer.extractText(line);

        assertEquals("片段1", text);
    }

    @Test
    void extractText_nonJsonLine_returnsNull() {
        ClaudeCodeProvider.StreamConsumer consumer = newConsumer(new ByteArrayInputStream(new byte[0]), true);

        String text = consumer.extractText("not a json line");

        assertNull(text);
    }

    @Test
    void streamConsumer_jsonLine_updatesLastEventType() throws Exception {
        InputStream in = new ByteArrayInputStream(
                "{\"type\":\"assistant\",\"content\":[]}\n".getBytes());
        ClaudeCodeProvider.StreamConsumer consumer = newConsumer(in, true);
        consumer.start();
        consumer.join(2000);

        assertEquals(1, consumer.getLineCount());
        assertEquals("assistant", consumer.getLastEventType());
    }

    @Test
    void streamConsumer_nonJsonLine_keepsPreviousEventType() throws Exception {
        InputStream in = new ByteArrayInputStream(
                "{\"type\":\"tool_use\"}\nnot a json line\n".getBytes());
        ClaudeCodeProvider.StreamConsumer consumer = newConsumer(in, true);
        consumer.start();
        consumer.join(2000);

        assertEquals(2, consumer.getLineCount());
        assertEquals("tool_use", consumer.getLastEventType());
    }

    @Test
    void streamConsumer_errorLine_doesNotThrow() throws Exception {
        String errorLine = "{\"type\":\"result\",\"is_error\":true,"
                + "\"api_error_status\":400,"
                + "\"result\":\"API Error: 400 {\\\"type\\\":\\\"error\\\","
                + "\\\"error\\\":{\\\"type\\\":\\\"invalid_request_error\\\","
                + "\\\"message\\\":\\\"model not found\\\"}}\"}";
        InputStream in = new ByteArrayInputStream((errorLine + "\n").getBytes());
        ClaudeCodeProvider.StreamConsumer consumer = newConsumer(in, true);
        consumer.start();
        consumer.join(2000);

        assertEquals(1, consumer.getLineCount());
        assertTrue(consumer.getCollected().contains("is_error"));
    }

    @Test
    void extractApiError_isErrorTrue_returnsResultText() {
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);
        String collected = "{\"type\":\"result\",\"subtype\":\"success\","
                + "\"is_error\":true,\"api_error_status\":400,"
                + "\"result\":\"API Error: 400 {\\\"type\\\":\\\"error\\\","
                + "\\\"error\\\":{\\\"type\\\":\\\"invalid_request_error\\\"}}\"}\n";

        String error = provider.extractApiError(collected);

        assertNotNull(error);
        assertTrue(error.contains("API Error"));
        assertTrue(error.contains("invalid_request_error"));
    }

    @Test
    void extractApiError_noError_returnsNull() {
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);
        String collected = "{\"type\":\"result\",\"subtype\":\"success\","
                + "\"is_error\":false,\"result\":\"点评完成\"}\n";

        String error = provider.extractApiError(collected);

        assertNull(error);
    }

    @Test
    void applyAnthropicEnv_bridgeModelSet_overridesAnthropicModelKeepsOtherVars() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("ANTHROPIC_MODEL", "glm-5.2");
        env.put("ANTHROPIC_BASE_URL", "https://dashscope.aliyuncs.com/apps/anthropic");
        env.put("ANTHROPIC_API_KEY", "sk-keep");
        env.put("PATH", "/usr/bin");

        TaskConfig cfg = new TaskConfig();
        cfg.setClaudeBridgeModel("qwen-plus");

        ClaudeCodeProvider.applyAnthropicEnv(env, cfg);

        assertEquals("qwen-plus", env.get("ANTHROPIC_MODEL"));
        assertEquals("qwen-plus", env.get("ANTHROPIC_SMALL_FAST_MODEL"));
        assertEquals("https://dashscope.aliyuncs.com/apps/anthropic", env.get("ANTHROPIC_BASE_URL"));
        assertEquals("sk-keep", env.get("ANTHROPIC_API_KEY"));
        assertEquals("/usr/bin", env.get("PATH"));
    }

    @Test
    void applyAnthropicEnv_blankBridgeModel_clearsAnthropicModel() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("ANTHROPIC_MODEL", "glm-5.2");
        env.put("ANTHROPIC_SMALL_FAST_MODEL", "claude-3-5-haiku-20241022");

        TaskConfig cfg = new TaskConfig();

        ClaudeCodeProvider.applyAnthropicEnv(env, cfg);

        assertNull(env.get("ANTHROPIC_MODEL"));
        assertNull(env.get("ANTHROPIC_SMALL_FAST_MODEL"));
    }

    @Test
    void applyAnthropicEnv_inheritedAuthRemoved_apiKeyPreserved() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("ANTHROPIC_API_KEY", "sk-keep");
        env.put("ANTHROPIC_AUTH_TOKEN", "sk-host-polluted");

        TaskConfig cfg = new TaskConfig();
        cfg.setClaudeBridgeModel("qwen-plus");

        ClaudeCodeProvider.applyAnthropicEnv(env, cfg);

        assertEquals("sk-keep", env.get("ANTHROPIC_API_KEY"));
        assertNull(env.get("ANTHROPIC_AUTH_TOKEN"));  // 清除冲突的 token
    }

    @Test
    void applyAnthropicEnv_inheritedSmallFastModel_isOverridden() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("ANTHROPIC_SMALL_FAST_MODEL", "claude-3-5-haiku-20241022");

        TaskConfig cfg = new TaskConfig();
        cfg.setClaudeBridgeModel("qwen-plus");

        ClaudeCodeProvider.applyAnthropicEnv(env, cfg);

        assertEquals("qwen-plus", env.get("ANTHROPIC_SMALL_FAST_MODEL"));
    }

    @Test
    void applyAnthropicEnv_smallModelConfigured_usesConfiguredSmallModel() {
        Map<String, String> env = new HashMap<String, String>();

        TaskConfig cfg = new TaskConfig();
        cfg.setClaudeBridgeModel("qwen3.7-max");
        cfg.setClaudeBridgeSmallModel("qwen-flash");

        ClaudeCodeProvider.applyAnthropicEnv(env, cfg);

        assertEquals("qwen3.7-max", env.get("ANTHROPIC_MODEL"));
        assertEquals("qwen-flash", env.get("ANTHROPIC_SMALL_FAST_MODEL"));
    }

    @Test
    void extractApiError_syntheticModel_returnsError() {
        TaskConfig config = newTaskConfig();
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config);
        String collected = "{\"type\":\"assistant\",\"message\":{\"id\":\"abc\","
                + "\"model\":\"<synthetic>\",\"role\":\"assistant\","
                + "\"stop_reason\":\"stop_sequence\",\"type\":\"message\"}}\n";

        String error = provider.extractApiError(collected);

        assertNotNull(error);
        assertTrue(error.contains("<synthetic>"));
        assertTrue(error.contains("stop_sequence"));
    }

    @Test
    void isErrorLine_syntheticModel_returnsTrue() {
        String line = "{\"type\":\"assistant\",\"message\":{\"model\":\"<synthetic>\"}}";

        assertTrue(ClaudeCodeProvider.isErrorLine(line));
    }

    @Test
    void isErrorLine_normalAssistant_returnsFalse() {
        String line = "{\"type\":\"assistant\",\"message\":{\"model\":\"qwen3.5-omni-plus\"}}";

        assertFalse(ClaudeCodeProvider.isErrorLine(line));
    }

    @Test
    void execute_timeout_callsOnError() throws Exception {
        final FakeProcess fake = new FakeProcess(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayInputStream(new byte[0]),
                0,
                true);
        TaskConfig config = newTaskConfig();
        config.setTimeoutMs(100L);
        ClaudeCodeProvider provider = new ClaudeCodeProvider(config) {
            @Override
            protected Process startProcess(String prompt, Path claudeHome, Path taskDir) {
                return fake;
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> err = new AtomicReference<Throwable>(null);
        StreamCallback cb = new StreamCallback() {
            @Override public void onToken(String token) {}
            @Override public void onComplete(String fullResponse) { latch.countDown(); }
            @Override public void onError(Throwable t) { err.set(t); latch.countDown(); }
        };

        provider.execute(new TaskRequest("user1", new byte[]{1}, "x.mp4", "hi", null), cb);
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        assertNotNull(err.get());
        assertTrue(err.get().getMessage().contains("任务超时"));
    }

    @Test
    void appendToolPolicy_emptyLists_addsNoFlags() {
        java.util.List<String> args = new java.util.ArrayList<String>();
        TaskConfig cfg = new TaskConfig();

        ClaudeCodeProvider.appendToolPolicy(args, cfg);

        assertTrue(args.isEmpty());
    }

    @Test
    void appendToolPolicy_allowedOnly_addsAllowedFlagCommaJoined() {
        java.util.List<String> args = new java.util.ArrayList<String>();
        TaskConfig cfg = new TaskConfig();
        cfg.setAllowedTools(java.util.Arrays.asList("Read", "Grep", "Bash(git log:*)"));

        ClaudeCodeProvider.appendToolPolicy(args, cfg);

        assertEquals(2, args.size());
        assertEquals("--allowedTools", args.get(0));
        assertEquals("Read,Grep,Bash(git log:*)", args.get(1));
    }

    @Test
    void appendToolPolicy_disallowedOnly_addsDisallowedFlag() {
        java.util.List<String> args = new java.util.ArrayList<String>();
        TaskConfig cfg = new TaskConfig();
        cfg.setDisallowedTools(java.util.Arrays.asList("Bash(rm:*)"));

        ClaudeCodeProvider.appendToolPolicy(args, cfg);

        assertEquals(2, args.size());
        assertEquals("--disallowedTools", args.get(0));
        assertEquals("Bash(rm:*)", args.get(1));
    }

    @Test
    void appendToolPolicy_both_addsAllowedThenDisallowed() {
        java.util.List<String> args = new java.util.ArrayList<String>();
        TaskConfig cfg = new TaskConfig();
        cfg.setAllowedTools(java.util.Arrays.asList("Read"));
        cfg.setDisallowedTools(java.util.Arrays.asList("Bash(rm:*)", "Write"));

        ClaudeCodeProvider.appendToolPolicy(args, cfg);

        assertEquals(4, args.size());
        assertEquals("--allowedTools", args.get(0));
        assertEquals("Read", args.get(1));
        assertEquals("--disallowedTools", args.get(2));
        assertEquals("Bash(rm:*),Write", args.get(3));
    }

    @Test
    void appendToolPolicy_blankEntries_skipped() {
        java.util.List<String> args = new java.util.ArrayList<String>();
        TaskConfig cfg = new TaskConfig();
        cfg.setAllowedTools(java.util.Arrays.asList("", "Read", ""));

        ClaudeCodeProvider.appendToolPolicy(args, cfg);

        assertEquals(2, args.size());
        assertEquals("Read", args.get(1));
    }

    private TaskConfig newTaskConfig() {
        TaskConfig config = new TaskConfig();
        config.setEnabled(true);
        config.setWorkspaceRoot(tempDir.getAbsolutePath());
        config.setClaudeHome(tempDir.getAbsolutePath());
        config.setTimeoutMs(5_000L);
        return config;
    }

    private ClaudeCodeProvider.StreamConsumer newConsumer(InputStream in, boolean parseJson) {
        return new ClaudeCodeProvider.StreamConsumer(in, null, parseJson, "stdout");
    }

    static class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;
        private final int exitCode;
        private volatile boolean alive;

        FakeProcess(InputStream stdout, InputStream stderr, int exitCode) {
            this(stdout, stderr, exitCode, false);
        }

        FakeProcess(InputStream stdout, InputStream stderr, int exitCode, boolean initialAlive) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.alive = initialAlive;
        }

        @Override public OutputStream getOutputStream() { return new OutputStream() {
            @Override public void write(int b) {}
        }; }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return stderr; }
        @Override public int waitFor() throws InterruptedException {
            while (alive) Thread.sleep(10);
            return exitCode;
        }
        @Override public boolean waitFor(long timeout, TimeUnit unit) {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            while (alive && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(10); } catch (InterruptedException e) { return false; }
            }
            return !alive;
        }
        @Override public int exitValue() {
            if (alive) throw new IllegalThreadStateException("not terminated");
            return exitCode;
        }
        @Override public void destroy() { alive = false; }
        @Override public Process destroyForcibly() { alive = false; return this; }
        @Override public boolean isAlive() { return alive; }
    }
}
