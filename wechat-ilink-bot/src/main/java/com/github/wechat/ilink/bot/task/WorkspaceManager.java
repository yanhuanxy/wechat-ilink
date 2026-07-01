package com.github.wechat.ilink.bot.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    public static final String DEFAULT_VIDEO_FILE_NAME = "input.mp4";

    private final String workspaceRoot;

    public WorkspaceManager(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Path prepareTaskDir(String userId, String taskId) throws IOException {
        Path dir = Paths.get(workspaceRoot, sanitize(userId), sanitize(taskId));
        Files.createDirectories(dir);
        return dir;
    }

    public Path writeVideo(Path taskDir, byte[] videoBytes, String originalFileName) throws IOException {
        String targetName = pickVideoFileName(originalFileName);
        Path target = taskDir.resolve(targetName);
        Files.write(target, videoBytes);
        log.info("视频已写入工作目录: {}", target);
        return target;
    }

    String pickVideoFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isEmpty()) {
            return DEFAULT_VIDEO_FILE_NAME;
        }
        String safe = originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.isEmpty() || !safe.matches(".*[a-zA-Z0-9].*")) {
            return DEFAULT_VIDEO_FILE_NAME;
        }
        return safe;
    }

    String sanitize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return "unknown";
        }
        String safe = segment.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isEmpty() ? "unknown" : safe;
    }
}
