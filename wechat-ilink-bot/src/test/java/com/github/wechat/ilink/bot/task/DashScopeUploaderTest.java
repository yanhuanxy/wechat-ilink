package com.github.wechat.ilink.bot.task;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeUploaderTest {

    @Test
    void parsePolicyResponse_validJson_returnsAllFields() throws Exception {
        String json = "{\"request_id\":\"abc\",\"data\":{"
                + "\"oss_access_key_id\":\"AKIDxxxx\",\"signature\":\"sig==\","
                + "\"policy\":\"POL==\",\"upload_dir\":\"u/dash/abc\","
                + "\"upload_host\":\"https://dashscope.oss-cn-beijing.aliyuncs.com\"}}";

        DashScopeUploader.UploadPolicy policy = DashScopeUploader.parsePolicyResponse(json);

        assertEquals("AKIDxxxx", policy.ossAccessKeyId);
        assertEquals("sig==", policy.signature);
        assertEquals("POL==", policy.policy);
        assertEquals("u/dash/abc", policy.uploadDir);
        assertEquals("https://dashscope.oss-cn-beijing.aliyuncs.com", policy.uploadHost);
    }

    @Test
    void parsePolicyResponse_missingData_throws() {
        String json = "{\"request_id\":\"abc\",\"msg\":\"ok\"}";
        Exception ex = assertThrows(Exception.class,
                () -> DashScopeUploader.parsePolicyResponse(json));
        assertTrue(ex.getMessage().contains("data"));
    }

    @Test
    void parsePolicyResponse_partialFields_throws() {
        String json = "{\"data\":{\"signature\":\"sig\",\"upload_dir\":\"d\"}}";
        Exception ex = assertThrows(Exception.class,
                () -> DashScopeUploader.parsePolicyResponse(json));
        assertTrue(ex.getMessage().contains("不完整"));
    }

    @Test
    void parsePolicyResponse_emptyJson_throws() {
        assertThrows(Exception.class,
                () -> DashScopeUploader.parsePolicyResponse(""));
        assertThrows(Exception.class,
                () -> DashScopeUploader.parsePolicyResponse(null));
    }

    @Test
    void buildMultipartBody_containsRequiredFieldsInOrder() throws Exception {
        DashScopeUploader.UploadPolicy policy = new DashScopeUploader.UploadPolicy();
        policy.ossAccessKeyId = "AKID";
        policy.signature = "SIG";
        policy.policy = "POL";
        policy.uploadDir = "u/abc";
        policy.uploadHost = "https://oss.example.com";

        byte[] videoBytes = "fake-mp4-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] body = DashScopeUploader.buildMultipartBody("BNDRY", policy, videoBytes, "video.mp4");
        String s = new String(body, StandardCharsets.UTF_8);

        int idxAccess = s.indexOf("name=\"OSSAccessKeyId\"");
        int idxSig = s.indexOf("name=\"Signature\"");
        int idxPolicy = s.indexOf("name=\"policy\"");
        int idxKey = s.indexOf("u/abc/video.mp4");
        int idxFile = s.indexOf("filename=\"video.mp4\"");

        assertTrue(idxAccess > 0, "OSSAccessKeyId 字段必须存在");
        assertTrue(idxSig > idxAccess, "Signature 应在 OSSAccessKeyId 之后");
        assertTrue(idxPolicy > idxSig, "policy 应在 Signature 之后");
        assertTrue(idxKey > idxPolicy, "key 值应在 policy 之后");
        assertTrue(idxFile > idxKey, "file 字段必须在最后");
        assertTrue(s.startsWith("--BNDRY\r\n"), "body 应以 boundary 开头");
        assertTrue(s.endsWith("--BNDRY--\r\n"), "body 应以 closing boundary 结尾");
        assertTrue(s.contains("\"AKID\"") == false && s.indexOf("AKID") > 0,
                "OSSAccessKeyId 值应被写入");
    }

    @Test
    void buildMultipartBody_includesRawVideoBytes() throws Exception {
        DashScopeUploader.UploadPolicy policy = policy();
        byte[] videoBytes = new byte[]{1, 2, 3, 4, 5};
        byte[] body = DashScopeUploader.buildMultipartBody("B", policy, videoBytes, "v.mp4");

        boolean found = false;
        for (int i = 0; i <= body.length - videoBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < videoBytes.length; j++) {
                if (body[i + j] != videoBytes[j]) { match = false; break; }
            }
            if (match) { found = true; break; }
        }
        assertTrue(found, "原始视频字节流必须原样出现在 multipart body 中");
    }

    @Test
    void buildOssUrl_concatenatesBucketDirAndFileName() {
        assertEquals("oss://u/abc/video.mp4",
                DashScopeUploader.buildOssUrl(
                        "https://dashscope-file-mgr.oss-cn-beijing.aliyuncs.com",
                        "u/abc", "video.mp4"));
    }

    @Test
    void pickFileName_validInput_returnsAsIs() {
        assertEquals("input_123.mp4",
                DashScopeUploader.pickFileName("input_123.mp4"));
    }

    @Test
    void pickFileName_nullOrEmpty_returnsDefault() {
        assertEquals("video.mp4", DashScopeUploader.pickFileName(null));
        assertEquals("video.mp4", DashScopeUploader.pickFileName(""));
    }

    @Test
    void pickFileName_stripsUnsafeChars() {
        assertEquals("input_video.mp4", DashScopeUploader.pickFileName("input video.mp4"));
        assertEquals("input__.mp4", DashScopeUploader.pickFileName("input视频.mp4"));
    }

    private DashScopeUploader.UploadPolicy policy() {
        DashScopeUploader.UploadPolicy p = new DashScopeUploader.UploadPolicy();
        p.ossAccessKeyId = "AKID";
        p.signature = "SIG";
        p.policy = "POL";
        p.uploadDir = "u/abc";
        p.uploadHost = "https://oss.example.com";
        return p;
    }
}
