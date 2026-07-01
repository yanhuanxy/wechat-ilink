package com.github.wechat.ilink.bot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.bot.config.TaskConfig;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaudeCodeProvider implements TaskProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final int PROMPT_PREVIEW_LEN = 200;
    private static final int STDERR_TAIL_LEN = 500;

    private final TaskConfig config;
    private final WorkspaceManager workspaceManager;

    public ClaudeCodeProvider(TaskConfig config) {
        this(config, new WorkspaceManager(config.getWorkspaceRoot()));
    }

    public ClaudeCodeProvider(TaskConfig config, WorkspaceManager workspaceManager) {
        this.config = config;
        this.workspaceManager = workspaceManager;
    }

    @Override
    public void execute(TaskRequest req, StreamCallback callback) {
        long start = System.currentTimeMillis();
        Process process = null;
        ScheduledExecutorService heartbeat = null;
        StreamConsumer stdout = null;
        StreamConsumer stderr = null;
        try {
            Path taskDir = workspaceManager.prepareTaskDir(req.getUserId(), req.getTaskId());
            Path videoFile = workspaceManager.writeVideo(taskDir, req.getVideoBytes(), req.getVideoFileName());
            Path claudeHome = ensureClaudeHome();

            String prompt = buildPrompt(req.getUserPrompt(), videoFile);
            process = startProcess(prompt, claudeHome, taskDir);

            stdout = new StreamConsumer(process.getInputStream(), callback, true, "stdout");
            stderr = new StreamConsumer(process.getErrorStream(), callback, false, "stderr");
            stdout.start();
            stderr.start();

            log.info("claude 子进程已启动: userId={}, taskId={}, claudeHome={}, taskDir={}, model={}, permissionMode={}, timeoutMs={}, promptPreview=[{}]",
                    req.getUserId(), req.getTaskId(), claudeHome, taskDir,
                    config.getVideoReviewModel(), config.getPermissionMode(), config.getTimeoutMs(), previewPrompt(prompt));

            heartbeat = startHeartbeat(req.getTaskId(), start, stdout, stderr);

            boolean finished = process.waitFor(config.getTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyForcibly(process);
                long elapsed = System.currentTimeMillis() - start;
                log.warn("claude 子进程超时被强杀: userId={}, taskId={}, timeoutMs={}, elapsedMs={}, stdoutLines={}, stderrLines={}, stderrTail=[{}]",
                        req.getUserId(), req.getTaskId(), config.getTimeoutMs(), elapsed,
                        stdout.getLineCount(), stderr.getLineCount(), tail(stderr.getCollected(), STDERR_TAIL_LEN));
                callback.onError(new RuntimeException("任务超时 (" + config.getTimeoutMs() / 1000 + "s)"));
                return;
            }

            stdout.join(2000);
            stderr.join(2000);

            int exitCode = process.exitValue();
            long elapsed = System.currentTimeMillis() - start;
            log.info("claude 子进程结束: userId={}, taskId={}, exitCode={}, elapsedMs={}, stdoutLines={}, stderrLines={}",
                    req.getUserId(), req.getTaskId(), exitCode, elapsed,
                    stdout.getLineCount(), stderr.getLineCount());

            if (exitCode != 0) {
                callback.onError(new RuntimeException("claude 退出码 " + exitCode + ", stderr=" + stderr.getCollected()));
                return;
            }

            String fullText = stdout.getFullText().trim();
            if (fullText.isEmpty()) {
                fullText = stderr.getCollected().trim();
            }

            String apiError = extractApiError(stdout.getCollected());
            if (apiError != null) {
                log.error("claude 子进程返回 API 错误: userId={}, taskId={}, elapsedMs={}, error={}",
                        req.getUserId(), req.getTaskId(), elapsed, apiError);
                callback.onError(new RuntimeException("模型 API 错误: " + apiError));
                return;
            }

            log.info("Claude Code 任务完成, userId={}, taskId={}, 输出长度={}",
                    req.getUserId(), req.getTaskId(), fullText.length());
            callback.onComplete(fullText);
        } catch (Exception e) {
            log.error("Claude Code 任务执行失败, userId={}, taskId={}",
                    req.getUserId(), req.getTaskId(), e);
            callback.onError(e);
        } finally {
            if (heartbeat != null) {
                heartbeat.shutdownNow();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private ScheduledExecutorService startHeartbeat(String taskId, long startMs,
                                                    final StreamConsumer stdout, final StreamConsumer stderr) {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "claude-heartbeat-" + (taskId == null ? "?" : taskId.substring(0, Math.min(8, taskId.length()))));
                t.setDaemon(true);
                return t;
            }
        });
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long elapsedSec = (System.currentTimeMillis() - startMs) / 1000;
                log.info("心跳: taskId={}, elapsed={}s, stdoutLines={}, stderrLines={}, lastStdoutEvent={}",
                        taskId, elapsedSec,
                        stdout.getLineCount(), stderr.getLineCount(), stdout.getLastEventType());
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return exec;
    }

    protected Process startProcess(String prompt, Path claudeHome, Path taskDir) throws IOException {
        java.util.List<String> args = new java.util.ArrayList<String>();
        args.add(config.getClaudePath());
        args.add("-p");
        args.add(prompt);
        args.add("--add-dir");
        args.add(taskDir.toAbsolutePath().toString());
        args.add("--output-format");
        args.add("stream-json");
        args.add("--verbose");
        String model = config.getVideoReviewModel();
        if (model != null && !model.isEmpty()) {
            args.add("--model");
            args.add(model);
        }
        args.add("--permission-mode");
        args.add(config.getPermissionMode());
        appendToolPolicy(args, config);

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(claudeHome.toFile());
        pb.redirectErrorStream(false);
        applyAnthropicEnv(pb.environment(), config);
        String baseUrl = config.getDashscopeBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            pb.environment().put("ANTHROPIC_BASE_URL", baseUrl);
        }
        String authToken = config.getDashscopeApiKey();
        if (authToken != null && !authToken.isEmpty()) {
            pb.environment().put("ANTHROPIC_API_KEY", authToken);
        }
        return pb.start();
    }

    public static void applyAnthropicEnv(Map<String, String> env, TaskConfig cfg) {
        // 清除可能从父进程继承的污染（dev 机器自己跑 Claude Code 时会带这些）
        env.remove("ANTHROPIC_MODEL");
        env.remove("ANTHROPIC_SMALL_FAST_MODEL");
        env.remove("ANTHROPIC_AUTH_TOKEN");   // 清除可能继承的 token，避免冲突
        String model = cfg.getClaudeBridgeModel();
        if (isSet(model)) {
            env.put("ANTHROPIC_MODEL", model);
            // 后台 haiku 调用：优先用配置的小快模型，未配置则复用主模型，都落到有效 DashScope 模型
            String smallModel = isSet(cfg.getClaudeBridgeSmallModel())
                    ? cfg.getClaudeBridgeSmallModel() : model;
            env.put("ANTHROPIC_SMALL_FAST_MODEL", smallModel);
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * 追加静态工具策略旗标（--allowedTools / --disallowedTools）。空列表不追加任何旗标，
     * 行为与未配置一致。仅在 permissionMode 非 bypassPermissions 时对 claude 实际生效。
     */
    public static void appendToolPolicy(List<String> args, TaskConfig cfg) {
        String allowed = joinComma(cfg.getAllowedTools());
        if (allowed != null) {
            args.add("--allowedTools");
            args.add(allowed);
        }
        String disallowed = joinComma(cfg.getDisallowedTools());
        if (disallowed != null) {
            args.add("--disallowedTools");
            args.add(disallowed);
        }
    }

    private static String joinComma(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String tool : tools) {
            if (tool == null || tool.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(tool);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    String buildPrompt(String userPrompt, Path videoFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("请使用 piano-practice-review skill 点评这段孩子钢琴练琴视频。\n\n");
        sb.append("视频文件绝对路径：").append(videoFile.toAbsolutePath().toString()).append("\n");
        sb.append("注意：SKILL.md 里写的 /mnt/user-data/uploads/ 和 /tmp/piano_frames ")
          .append("是 sandbox 占位路径，本机环境请用上面给出的绝对路径，")
          .append("抽帧输出请放到系统临时目录（如 java.io.tmpdir 下的 piano_frames_<taskId>）。\n\n");
        if (userPrompt != null && !userPrompt.isEmpty()) {
            sb.append("家长留言：").append(userPrompt).append("\n");
        }
        return sb.toString();
    }

    private Path ensureClaudeHome() throws IOException {
        Path home = Paths.get(config.getClaudeHome());
        Files.createDirectories(home);
        return home;
    }

    private static String previewPrompt(String prompt) {
        if (prompt == null) return "";
        String oneLine = prompt.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() > PROMPT_PREVIEW_LEN
                ? oneLine.substring(0, PROMPT_PREVIEW_LEN) + "..."
                : oneLine;
    }

    private static String tail(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(s.length() - max) : s;
    }

    static boolean isErrorLine(String line) {
        return line != null
                && (line.contains("\"is_error\":true")
                        || line.contains("\"api_error_status\":")
                        || line.contains("API Error:")
                        || line.contains("\"type\":\"error\"")
                        || line.contains("\"model\":\"<synthetic>\""));
    }

    String extractApiError(String collected) {
        if (collected == null || collected.isEmpty()) return null;
        for (String line : collected.split("\n")) {
            try {
                JsonNode node = MAPPER.readTree(line);
                JsonNode isError = node.get("is_error");
                if (isError != null && isError.asBoolean(false)) {
                    JsonNode result = node.get("result");
                    return result != null && result.isTextual()
                            ? result.asText() : node.toString();
                }
                JsonNode message = node.get("message");
                if (message != null && message.has("model")) {
                    String model = message.get("model").asText();
                    if ("<synthetic>".equals(model)) {
                        String stopReason = message.has("stop_reason")
                                ? message.get("stop_reason").asText() : "unknown";
                        return "Claude Code 返回合成消息 (model=<synthetic>, stop_reason=" + stopReason
                                + ")，上游 API 调用失败";
                    }
                }
            } catch (Exception ignored) {
                // 非 JSON 行跳过
            }
        }
        return null;
    }

    private void destroyForcibly(Process p) {
        try {
            p.destroyForcibly().waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static class StreamConsumer extends Thread {
        private static final Logger STREAM_LOG = LoggerFactory.getLogger(StreamConsumer.class);

        private final InputStream in;
        private final StreamCallback callback;
        private final boolean parseJson;
        private final String streamName;
        private final StringBuilder collected = new StringBuilder();
        private final StringBuilder fullText = new StringBuilder();
        private final AtomicInteger lineCount = new AtomicInteger(0);
        private volatile String lastEventType;

        StreamConsumer(InputStream in, StreamCallback callback, boolean parseJson, String streamName) {
            this.in = in;
            this.callback = callback;
            this.parseJson = parseJson;
            this.streamName = streamName;
            setDaemon(true);
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount.incrementAndGet();
                    collected.append(line).append("\n");
                    if (isErrorLine(line)) {
                        STREAM_LOG.warn("[{}] ERROR-LINE: {}", streamName, line);
                    } else {
                        STREAM_LOG.debug("[{}] {}", streamName,
                                line.length() > PROMPT_PREVIEW_LEN
                                        ? line.substring(0, PROMPT_PREVIEW_LEN) + "..." : line);
                    }
                    String eventType = parseEventType(line);
                    if (eventType != null) {
                        lastEventType = eventType;
                    }
                    if (!parseJson) continue;
                    String text = extractText(line);
                    if (text != null && !text.isEmpty()) {
                        fullText.append(text);
                        callback.onToken(text);
                    }
                }
            } catch (IOException e) {
                // stream closed by process termination
            }
        }

        String parseEventType(String line) {
            try {
                JsonNode node = MAPPER.readTree(line);
                JsonNode type = node.get("type");
                if (type != null && type.isTextual()) {
                    return type.asText();
                }
            } catch (Exception ignored) {
                // non-JSON line
            }
            return null;
        }

        String extractText(String line) {
            try {
                JsonNode node = MAPPER.readTree(line);
                JsonNode delta = node.get("content_block_delta");
                if (delta != null && delta.has("text")) {
                    return delta.get("text").asText();
                }
                JsonNode result = node.get("result");
                if (result != null && result.isTextual()) {
                    return result.asText();
                }
                JsonNode message = node.get("message");
                if (message != null && message.has("content")) {
                    return extractContentArray(message.get("content"));
                }
            } catch (Exception ignored) {
                // non-JSON line, skip
            }
            return null;
        }

        private String extractContentArray(JsonNode content) {
            if (!content.isArray()) return null;
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                JsonNode text = part.get("text");
                if (text != null) {
                    sb.append(text.asText());
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        int getLineCount() {
            return lineCount.get();
        }

        String getLastEventType() {
            return lastEventType;
        }

        String getCollected() {
            return collected.toString();
        }

        String getFullText() {
            return fullText.toString();
        }
    }
}
