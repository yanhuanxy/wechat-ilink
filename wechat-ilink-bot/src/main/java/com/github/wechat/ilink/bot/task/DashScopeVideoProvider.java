package com.github.wechat.ilink.bot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.wechat.ilink.bot.config.TaskConfig;
import com.github.wechat.ilink.bot.llm.StreamCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 走 DashScope OpenAI 兼容端点直连：上传视频 → POST /chat/completions 带 video_url 块 → 返回点评。
 *
 * 不经过 claude CLI，也不经过 piano-practice-review skill，避开 CLI 无法构造视频块的死路。
 * 点评风格直接由系统提示词驱动。
 */
public class DashScopeVideoProvider implements TaskProvider {

    private static final Logger log = LoggerFactory.getLogger(DashScopeVideoProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private static final String FALLBACK_SYSTEM_PROMPT =
            "你是一位温暖、有经验的少儿钢琴老师，请用中文对孩子练琴视频做点评。" +
            "结构：先一句总体印象，然后从手型/坐姿/节奏/音准/表现力中挑 2-3 个最相关的展开，" +
            "每个点都基于视频里实际看到的细节，最后给一句鼓励。" +
            "语气亲切、具体、避免空话；不要列没看到的细节，不要编造曲名或小节数。";
    private static final String RUBRIC_BASE_PATH =
            ".claude/skills/piano-practice-review/references/rubrics";
    private static final String DEFAULT_RUBRIC_ID = "beginner";

    private final TaskConfig config;
    private final DashScopeUploader uploader;

    public DashScopeVideoProvider(TaskConfig config) {
        this(config, new DashScopeUploader(config));
    }

    public DashScopeVideoProvider(TaskConfig config, DashScopeUploader uploader) {
        this.config = config;
        this.uploader = uploader;
    }

    @Override
    public void execute(TaskRequest req, StreamCallback callback) {
        long start = System.currentTimeMillis();
        try {
            if (req.getVideoBytes() == null || req.getVideoBytes().length == 0) {
                callback.onError(new IllegalArgumentException("视频内容为空"));
                return;
            }
            log.info("DashScope 视频任务开始: userId={}, taskId={}, size={}B, model={}",
                    req.getUserId(), req.getTaskId(), req.getVideoBytes().length, config.getVideoReviewModel());

            String ossUrl = uploader.uploadVideo(req.getVideoBytes(), req.getVideoFileName());
            String review = callChat(ossUrl, req.getUserPrompt(), req.getRubricId());

            long elapsed = System.currentTimeMillis() - start;
            log.info("DashScope 视频任务完成: userId={}, taskId={}, elapsedMs={}, chars={}",
                    req.getUserId(), req.getTaskId(), elapsed, review.length());
            callback.onComplete(review);
        } catch (Exception e) {
            log.error("DashScope 视频任务失败: userId={}, taskId={}",
                    req.getUserId(), req.getTaskId(), e);
            callback.onError(e);
        }
    }

    String callChat(String ossUrl, String userPrompt, String rubricId) throws Exception {
        String systemPrompt = buildSystemPrompt(rubricId, config.getClaudeHome());
        String requestBody = buildRequestBody(ossUrl, userPrompt, config.getVideoReviewModel(), systemPrompt);
        String endpoint = trimTrailingSlash(config.getDashscopeBaseUrl()) + "/chat/completions";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.getDashscopeApiKey());
        conn.setRequestProperty("X-DashScope-OssResourceResolve", "enable");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        os.close();

        int status = conn.getResponseCode();
        String raw = readAll(status >= 200 && status < 300
                ? conn.getInputStream() : conn.getErrorStream());
        if (status < 200 || status >= 300) {
            throw new IOException("DashScope chat 失败 status=" + status
                    + " body=" + truncate(raw, 800));
        }
        return parseSseContent(raw);
    }

    static String buildRequestBody(String ossUrl, String userPrompt, String model,
                                   String systemPrompt) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("stream", true);

        ArrayNode modalities = MAPPER.createArrayNode();
        modalities.add("text");
        root.set("modalities", modalities);

        ObjectNode streamOptions = MAPPER.createObjectNode();
        streamOptions.put("include_usage", true);
        root.set("stream_options", streamOptions);

        ArrayNode messages = MAPPER.createArrayNode();
        ObjectNode sys = MAPPER.createObjectNode();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        messages.add(sys);

        ObjectNode user = MAPPER.createObjectNode();
        user.put("role", "user");
        ArrayNode content = MAPPER.createArrayNode();

        ObjectNode videoBlock = MAPPER.createObjectNode();
        videoBlock.put("type", "video_url");
        ObjectNode videoUrl = MAPPER.createObjectNode();
        videoUrl.put("url", ossUrl);
        videoBlock.set("video_url", videoUrl);
        content.add(videoBlock);

        ObjectNode textBlock = MAPPER.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", buildUserPrompt(userPrompt));
        content.add(textBlock);

        user.set("content", content);
        messages.add(user);
        root.set("messages", messages);

        return MAPPER.writeValueAsString(root);
    }

    static String parseSseContent(String sseBody) throws IOException {
        if (sseBody == null || sseBody.isEmpty()) {
            throw new IOException("chat SSE 响应为空");
        }
        StringBuilder out = new StringBuilder();
        for (String rawLine : sseBody.split("\n")) {
            String line = rawLine.trim();
            if (!line.startsWith("data:")) continue;
            String payload = line.substring("data:".length()).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) continue;
            JsonNode root = MAPPER.readTree(payload);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                continue;
            }
            JsonNode delta = choices.get(0).get("delta");
            if (delta != null && delta.get("content") != null) {
                String piece = delta.get("content").asText("");
                if (piece != null && !piece.isEmpty()) {
                    out.append(piece);
                }
            }
        }
        if (out.length() == 0) {
            throw new IOException("chat SSE 响应未产生文本: " + truncate(sseBody, 500));
        }
        return out.toString();
    }

    static String buildSystemPrompt(String rubricId, String claudeHome) {
        String targetId = (rubricId == null || rubricId.isEmpty())
                ? DEFAULT_RUBRIC_ID : rubricId;
        String content = loadRubricContent(targetId, claudeHome);
        if (!content.isEmpty()) return content;
        if (!DEFAULT_RUBRIC_ID.equals(targetId)) {
            content = loadRubricContent(DEFAULT_RUBRIC_ID, claudeHome);
            if (!content.isEmpty()) return content;
        }
        return FALLBACK_SYSTEM_PROMPT;
    }

    static String loadRubricContent(String rubricId, String claudeHome) {
        if (rubricId == null || rubricId.isEmpty()
                || claudeHome == null || claudeHome.isEmpty()) {
            return "";
        }
        Path path = Paths.get(claudeHome, RUBRIC_BASE_PATH, rubricId + ".md");
        if (!Files.exists(path)) return "";
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取 rubric 失败: path={}, err={}", path, e.getMessage());
            return "";
        }
    }

    private static String buildUserPrompt(String userPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("请点评这段孩子练琴视频。");
        if (userPrompt != null && !userPrompt.isEmpty()) {
            sb.append("家长留言：").append(userPrompt);
        }
        return sb.toString();
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "<null>";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static String readAll(InputStream in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception ignored) {
        } finally {
            try { reader.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }
}
