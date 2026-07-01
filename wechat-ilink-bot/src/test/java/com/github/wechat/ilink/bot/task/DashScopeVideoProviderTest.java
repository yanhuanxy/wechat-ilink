package com.github.wechat.ilink.bot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeVideoProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DUMMY_PROMPT = "你是钢琴老师";

    @TempDir
    Path tempClaudeHome;

    @Test
    void buildRequestBody_containsStreamModalitiesAndContentBlocks() throws Exception {
        String body = DashScopeVideoProvider.buildRequestBody(
                "oss://u/abc/video.mp4", "节奏稳一点", "qwen3.5-omni-plus", DUMMY_PROMPT);

        JsonNode root = MAPPER.readTree(body);
        assertEquals("qwen3.5-omni-plus", root.get("model").asText());
        assertTrue(root.get("stream").asBoolean(), "Qwen-Omni 要求 stream=true");
        assertNull(root.get("max_tokens"), "Qwen-Omni 不使用 max_tokens");

        JsonNode modalities = root.get("modalities");
        assertNotNull(modalities, "modalities 字段必须存在");
        assertTrue(modalities.isArray() && modalities.size() == 1, "modalities 应只含 text");
        assertEquals("text", modalities.get(0).asText());

        JsonNode streamOptions = root.get("stream_options");
        assertNotNull(streamOptions, "stream_options 字段必须存在");
        assertTrue(streamOptions.get("include_usage").asBoolean());

        JsonNode messages = root.get("messages");
        assertEquals(2, messages.size(), "应含 system + user 两条消息");
        assertEquals("system", messages.get(0).get("role").asText());

        JsonNode userContent = messages.get(1).get("content");
        assertTrue(userContent.isArray() && userContent.size() == 2,
                "user 消息应含 video + text 两个 content block");

        JsonNode block0 = userContent.get(0);
        assertEquals("video_url", block0.get("type").asText());
        assertEquals("oss://u/abc/video.mp4",
                block0.get("video_url").get("url").asText());

        JsonNode block1 = userContent.get(1);
        assertEquals("text", block1.get("type").asText());
        assertTrue(block1.get("text").asText().contains("节奏稳一点"));
        assertTrue(block1.get("text").asText().contains("点评"));
    }

    @Test
    void buildRequestBody_usesProvidedSystemPrompt() throws Exception {
        String customPrompt = "CUSTOM_PROMPT_MARKER_42";
        String body = DashScopeVideoProvider.buildRequestBody(
                "oss://x/y/z.mp4", "", "qwen3.5-omni-plus", customPrompt);

        JsonNode sys = MAPPER.readTree(body).get("messages").get(0);
        assertEquals("system", sys.get("role").asText());
        assertEquals(customPrompt, sys.get("content").asText(),
                "传入的 systemPrompt 必须原样塞进 system 消息");
    }

    @Test
    void buildRequestBody_blankUserPrompt_omitsParentNoteButStillHasReviewRequest() throws Exception {
        String body = DashScopeVideoProvider.buildRequestBody(
                "oss://x/y/z.mp4", "", "qwen3.5-omni-plus", DUMMY_PROMPT);

        JsonNode root = MAPPER.readTree(body);
        JsonNode textBlock = root.get("messages").get(1).get("content").get(1);
        String text = textBlock.get("text").asText();
        assertTrue(text.contains("点评"));
        assertFalse(text.contains("家长留言"));
    }

    @Test
    void parseSseContent_multipleDataChunks_concatenatesDeltaContent() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"手\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"型放松\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n"
                + "data: [DONE]\n";

        assertEquals("手型放松", DashScopeVideoProvider.parseSseContent(sse));
    }

    @Test
    void parseSseContent_emptyBody_throws() {
        assertThrows(Exception.class,
                () -> DashScopeVideoProvider.parseSseContent(null));
        assertThrows(Exception.class,
                () -> DashScopeVideoProvider.parseSseContent(""));
    }

    @Test
    void parseSseContent_onlyDoneMarker_throws() {
        String sse = "data: [DONE]\n";
        Exception ex = assertThrows(Exception.class,
                () -> DashScopeVideoProvider.parseSseContent(sse));
        assertTrue(ex.getMessage().contains("未产生文本"));
    }

    @Test
    void parseSseContent_deltaWithNullContent_doesNotAppend() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":null}}]}\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"X\"}}]}\n"
                + "data: [DONE]\n";
        assertEquals("X", DashScopeVideoProvider.parseSseContent(sse));
    }

    @Test
    void parseSseContent_missingChoicesField_skippedGracefully() throws Exception {
        String sse = "data: {\"choices\":[]}\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Y\"}}]}\n"
                + "data: [DONE]\n";
        assertEquals("Y", DashScopeVideoProvider.parseSseContent(sse));
    }

    @Test
    void loadRubricContent_fileExists_returnsContent() throws Exception {
        Path rubricDir = tempClaudeHome.resolve(
                ".claude/skills/piano-practice-review/references/rubrics");
        Files.createDirectories(rubricDir);
        Files.write(rubricDir.resolve("beginner.md"),
                "RUBRIC_BODY".getBytes(StandardCharsets.UTF_8));

        String content = DashScopeVideoProvider.loadRubricContent(
                "beginner", tempClaudeHome.toString());

        assertEquals("RUBRIC_BODY", content);
    }

    @Test
    void loadRubricContent_fileMissing_returnsEmpty() {
        assertEquals("", DashScopeVideoProvider.loadRubricContent(
                "beginner", tempClaudeHome.toString()));
    }

    @Test
    void loadRubricContent_nullInputs_returnsEmpty() {
        assertEquals("", DashScopeVideoProvider.loadRubricContent(null, "/tmp"));
        assertEquals("", DashScopeVideoProvider.loadRubricContent("", "/tmp"));
        assertEquals("", DashScopeVideoProvider.loadRubricContent("beginner", null));
        assertEquals("", DashScopeVideoProvider.loadRubricContent("beginner", ""));
    }

    @Test
    void buildSystemPrompt_validRubric_returnsFileContent() throws Exception {
        writeRubric(tempClaudeHome, "beginner", "BEGINNER_BODY");

        assertEquals("BEGINNER_BODY",
                DashScopeVideoProvider.buildSystemPrompt("beginner", tempClaudeHome.toString()));
    }

    @Test
    void buildSystemPrompt_unknownRubricId_fallsBackToBeginner() throws Exception {
        writeRubric(tempClaudeHome, "beginner", "BEGINNER_BODY");

        assertEquals("BEGINNER_BODY",
                DashScopeVideoProvider.buildSystemPrompt("expert", tempClaudeHome.toString()));
    }

    @Test
    void buildSystemPrompt_nullRubricId_butBeginnerFileExists_returnsBeginner() throws Exception {
        writeRubric(tempClaudeHome, "beginner", "BEGINNER_BODY");

        assertEquals("BEGINNER_BODY",
                DashScopeVideoProvider.buildSystemPrompt(null, tempClaudeHome.toString()));
    }

    @Test
    void buildSystemPrompt_allFilesMissing_returnsFallbackPrompt() {
        String prompt = DashScopeVideoProvider.buildSystemPrompt(
                "beginner", tempClaudeHome.toString());
        assertNotNull(prompt);
        assertTrue(prompt.contains("钢琴老师"), "兜底 prompt 应包含标志性词");
    }

    private static void writeRubric(Path claudeHome, String rubricId, String body)
            throws IOException {
        Path dir = claudeHome.resolve(
                ".claude/skills/piano-practice-review/references/rubrics");
        Files.createDirectories(dir);
        Files.write(dir.resolve(rubricId + ".md"), body.getBytes(StandardCharsets.UTF_8));
    }
}
