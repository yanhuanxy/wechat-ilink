package com.github.wechat.ilink.bot.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.bot.config.TaskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 通过 DashScope 的 /api/v1/uploads 接口上传视频文件，返回可被模型引用的 oss:// 临时 URL。
 *
 * 流程：
 *   1. GET {uploadsUrl}?action=getPolicy&model={model}，带 Authorization: Bearer {authToken}
 *      返回 data: { oss_access_key_id, signature, policy, upload_dir, upload_host }
 *   2. POST {upload_host}，multipart/form-data，字段顺序按 OSS 约定（file 必须最后）
 *      字段: OSSAccessKeyId / Signature / policy / key / file
 *   3. 构造临时 URL：oss://{upload_dir}/{fileName}
 *
 * 设计原则：HTTP I/O 不可单元测试，但请求体构造、响应解析、URL 拼接都是纯函数，
 *           拆出来做单元测试；端到端验证留给 live test。
 */
public class DashScopeUploader {

    private static final Logger log = LoggerFactory.getLogger(DashScopeUploader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final String DEFAULT_VIDEO_FILENAME = "video.mp4";

    private final TaskConfig config;

    public DashScopeUploader(TaskConfig config) {
        this.config = config;
    }

    /**
     * 上传视频字节流到 DashScope 临时存储，返回 oss:// URL 供模型引用。
     *
     * @param videoBytes 视频原始字节；为空将抛 IllegalArgumentException
     * @param fileName   OSS 上的文件名（仅用作 key 后缀）；null/空则用默认 video.mp4
     */
    public String uploadVideo(byte[] videoBytes, String fileName) throws Exception {
        if (videoBytes == null || videoBytes.length == 0) {
            throw new IllegalArgumentException("videoBytes 为空");
        }
        String safeName = pickFileName(fileName);
        log.info("开始上传视频到 DashScope: size={}B, fileName={}, model={}",
                videoBytes.length, safeName, config.getVideoReviewModel());

        UploadPolicy policy = fetchPolicy(config.getVideoReviewModel());
        log.debug("获取上传凭证成功: upload_dir={}, upload_host={}",
                policy.uploadDir, policy.uploadHost);

        int status = postToOss(policy, videoBytes, safeName);
        if (status != 200 && status != 204) {
            throw new IOException("OSS 上传失败 status=" + status
                    + " uploadHost=" + policy.uploadHost);
        }

        String ossUrl = buildOssUrl(policy.uploadHost, policy.uploadDir, safeName);
        log.info("视频上传成功: ossUrl={}, size={}B", ossUrl, videoBytes.length);
        return ossUrl;
    }

    UploadPolicy fetchPolicy(String model) throws Exception {
        String url = config.getDashscopeUploadsUrl()
                + "?action=getPolicy&model="
                + URLEncoder.encode(model == null ? "" : model, "UTF-8");
        HttpURLConnection conn = openGet(url);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + config.getDashscopeApiKey());
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        int status = conn.getResponseCode();
        String body = readAll(status >= 200 && status < 300
                ? conn.getInputStream() : conn.getErrorStream());
        if (status < 200 || status >= 300) {
            throw new IOException("获取上传凭证失败 status=" + status
                    + " body=" + truncate(body, 500));
        }
        log.info("上传凭证响应: {}", truncate(body, 1500));
        return parsePolicyResponse(body);
    }

    int postToOss(UploadPolicy policy, byte[] videoBytes, String fileName) throws Exception {
        String boundary = "----dashscope" + UUID.randomUUID().toString();
        HttpURLConnection conn = openPost(policy.uploadHost);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        byte[] body = buildMultipartBody(boundary, policy, videoBytes, fileName);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        os.close();

        int status = conn.getResponseCode();
        if (status != 200 && status != 204) {
            String err = readAll(conn.getErrorStream());
            log.error("OSS 上传失败: status={}, uploadHost={}, body={}",
                    status, policy.uploadHost, truncate(err, 800));
        } else {
            log.info("OSS 上传 HTTP 完成: status={}, size={}B", status, videoBytes.length);
        }
        return status;
    }

    static UploadPolicy parsePolicyResponse(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            throw new IOException("上传凭证响应为空");
        }
        JsonNode root = MAPPER.readTree(json);
        JsonNode data = root.get("data");
        if (data == null) {
            throw new IOException("上传凭证响应缺少 data 字段: " + truncate(json, 300));
        }
        UploadPolicy policy = new UploadPolicy();
        policy.ossAccessKeyId = textOrEmpty(data, "oss_access_key_id");
        policy.signature = textOrEmpty(data, "signature");
        policy.policy = textOrEmpty(data, "policy");
        policy.uploadDir = textOrEmpty(data, "upload_dir");
        policy.uploadHost = textOrEmpty(data, "upload_host");
        policy.xOssObjectAcl = textOrEmpty(data, "x_oss_object_acl");
        policy.xOssForbidOverwrite = textOrEmpty(data, "x_oss_forbid_overwrite");
        if (policy.signature.isEmpty() || policy.policy.isEmpty()
                || policy.uploadDir.isEmpty() || policy.uploadHost.isEmpty()) {
            throw new IOException("上传凭证字段不完整: " + truncate(json, 300));
        }
        return policy;
    }

    static byte[] buildMultipartBody(String boundary, UploadPolicy policy,
                                     byte[] videoBytes, String fileName) throws IOException {
        String dash = "--" + boundary + "\r\n";
        String end = "--" + boundary + "--\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append(dash).append(fieldHeader("OSSAccessKeyId")).append(policy.ossAccessKeyId).append("\r\n");
        sb.append(dash).append(fieldHeader("Signature")).append(policy.signature).append("\r\n");
        sb.append(dash).append(fieldHeader("policy")).append(policy.policy).append("\r\n");
        sb.append(dash).append(fieldHeader("key")).append(policy.uploadDir).append("/").append(fileName).append("\r\n");
        if (policy.xOssObjectAcl != null && !policy.xOssObjectAcl.isEmpty()) {
            sb.append(dash).append(fieldHeader("x-oss-object-acl")).append(policy.xOssObjectAcl).append("\r\n");
        }
        if (policy.xOssForbidOverwrite != null && !policy.xOssForbidOverwrite.isEmpty()) {
            sb.append(dash).append(fieldHeader("x-oss-forbid-overwrite")).append(policy.xOssForbidOverwrite).append("\r\n");
        }
        sb.append(dash).append(fileHeader(fileName));
        byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n" + end).getBytes(StandardCharsets.UTF_8);

        byte[] out = new byte[head.length + videoBytes.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(videoBytes, 0, out, head.length, videoBytes.length);
        System.arraycopy(tail, 0, out, head.length + videoBytes.length, tail.length);
        return out;
    }

    static String buildOssUrl(String uploadHost, String uploadDir, String fileName) {
        return "oss://" + uploadDir + "/" + fileName;
    }

    static String pickFileName(String input) {
        if (input == null || input.isEmpty()) {
            return DEFAULT_VIDEO_FILENAME;
        }
        String safe = input.replaceAll("[^a-zA-Z0-9._-]", "_");
        return (safe.isEmpty() || !safe.matches(".*[a-zA-Z0-9].*"))
                ? DEFAULT_VIDEO_FILENAME : safe;
    }

    private static String fieldHeader(String name) {
        return "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n";
    }

    private static String fileHeader(String fileName) {
        return "Content-Disposition: form-data; name=\"file\"; filename=\""
                + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null ? "" : v.asText("");
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
                sb.append(line);
            }
        } catch (Exception ignored) {
        } finally {
            try { reader.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    /** 可被测试子类覆盖以注入 mock HttpURLConnection。 */
    protected HttpURLConnection openGet(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    /** 可被测试子类覆盖以注入 mock HttpURLConnection。 */
    protected HttpURLConnection openPost(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    static final class UploadPolicy {
        String ossAccessKeyId;
        String signature;
        String policy;
        String uploadDir;
        String uploadHost;
        String xOssObjectAcl;
        String xOssForbidOverwrite;
    }
}
