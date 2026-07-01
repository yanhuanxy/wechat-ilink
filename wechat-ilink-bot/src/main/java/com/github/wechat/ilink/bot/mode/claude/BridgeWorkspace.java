package com.github.wechat.ilink.bot.mode.claude;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude Bridge 文件工作目录：每用户 {@code <cwd>/<userId>/{input,output}}。
 * 入向文件写入 input/，Claude 产物约定写入 output/，跑完扫描回发。纯 nio，SDK-free。
 */
public class BridgeWorkspace {

    private static final Logger log = LoggerFactory.getLogger(BridgeWorkspace.class);
    private static final String DEFAULT_INPUT_NAME = "input.bin";

    private final String cwd;

    public BridgeWorkspace(String cwd) {
        this.cwd = cwd;
    }

    public Path writeInput(String userId, byte[] bytes, String fileName) throws IOException {
        Path inputDir = Paths.get(cwd, sanitize(userId), "input");
        Files.createDirectories(inputDir);
        Path target = inputDir.resolve(pickFileName(fileName));
        Files.write(target, bytes);
        log.info("入向文件已写入: {}", target);
        return target;
    }

    /** 返回 output/ 目录（先清空旧内容再重建），防止上一回合产物被重复回发。 */
    public Path freshOutputDir(String userId) throws IOException {
        Path outputDir = Paths.get(cwd, sanitize(userId), "output");
        if (Files.exists(outputDir)) {
            deleteChildren(outputDir);
        }
        Files.createDirectories(outputDir);
        return outputDir;
    }

    public List<Path> collectOutputs(Path outputDir) throws IOException {
        List<Path> out = new ArrayList<Path>();
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            return out;
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(outputDir);
        try {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        } finally {
            stream.close();
        }
        return out;
    }

    private void deleteChildren(Path dir) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
        try {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    deleteChildren(p);
                }
                Files.deleteIfExists(p);
            }
        } finally {
            stream.close();
        }
    }

    String pickFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isEmpty()) {
            return DEFAULT_INPUT_NAME;
        }
        String safe = originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.isEmpty() || !safe.matches(".*[a-zA-Z0-9].*")) {
            return DEFAULT_INPUT_NAME;
        }
        return safe;
    }

    static String sanitize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return "unknown";
        }
        String safe = segment.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isEmpty() ? "unknown" : safe;
    }
}
