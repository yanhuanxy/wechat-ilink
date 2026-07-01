package com.github.wechat.ilink.bot.mode.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgeWorkspaceTest {

    @TempDir
    java.io.File tempDir;

    private BridgeWorkspace workspace() {
        return new BridgeWorkspace(tempDir.getAbsolutePath());
    }

    @Test
    void writeInput_createsFileWithContent() throws IOException {
        BridgeWorkspace ws = workspace();
        byte[] bytes = "payload".getBytes();

        Path target = ws.writeInput("user1", bytes, "a.txt");

        assertTrue(Files.exists(target));
        assertArrayEquals(bytes, Files.readAllBytes(target));
        assertEquals("input", target.getParent().getFileName().toString());
        assertEquals("a.txt", target.getFileName().toString());
    }

    @Test
    void writeInput_nullFileName_usesDefaultInputBin() throws IOException {
        BridgeWorkspace ws = workspace();

        Path target = ws.writeInput("user1", new byte[]{1}, null);

        assertEquals("input.bin", target.getFileName().toString());
        assertTrue(Files.exists(target));
    }

    @Test
    void writeInput_unsafeFileName_staysInsideInputDir() throws IOException {
        BridgeWorkspace ws = workspace();

        Path target = ws.writeInput("user1", new byte[]{1}, "../evil.txt");

        // 经净化后文件名不含路径分隔，落点必在 input/ 下
        assertEquals("input", target.getParent().getFileName().toString());
        assertTrue(target.toAbsolutePath().startsWith(tempDir.toPath().toAbsolutePath()));
        assertFalse(target.getFileName().toString().contains("/"));
        assertTrue(Files.exists(target));
    }

    @Test
    void freshOutputDir_clearsPreviousOutputs() throws IOException {
        BridgeWorkspace ws = workspace();
        Path outputDir = ws.freshOutputDir("user1");
        Files.write(outputDir.resolve("stale.txt"), new byte[]{1});
        assertEquals(1, ws.collectOutputs(outputDir).size());

        Path refreshed = ws.freshOutputDir("user1");

        assertTrue(ws.collectOutputs(refreshed).isEmpty());
    }

    @Test
    void collectOutputs_returnsRegularFilesOnly() throws IOException {
        BridgeWorkspace ws = workspace();
        Path outputDir = ws.freshOutputDir("user1");
        Files.write(outputDir.resolve("a.txt"), new byte[]{1});
        Files.createDirectory(outputDir.resolve("subdir")); // 子目录不应被收集

        List<Path> files = ws.collectOutputs(outputDir);

        assertEquals(1, files.size());
        assertEquals("a.txt", files.get(0).getFileName().toString());
    }

    @Test
    void collectOutputs_nullOrMissingDir_returnsEmpty() throws IOException {
        BridgeWorkspace ws = workspace();

        assertTrue(ws.collectOutputs(null).isEmpty());
        assertTrue(ws.collectOutputs(tempDir.toPath().resolve("nope")).isEmpty());
    }

    @Test
    void pickFileName_nullOrEmpty_returnsDefault() {
        BridgeWorkspace ws = workspace();

        assertEquals("input.bin", ws.pickFileName(null));
        assertEquals("input.bin", ws.pickFileName(""));
        assertEquals("input.bin", ws.pickFileName("   "));
    }

    @Test
    void pickFileName_allSymbols_returnsDefault() {
        BridgeWorkspace ws = workspace();

        assertEquals("input.bin", ws.pickFileName("..."));
    }

    @Test
    void sanitize_nullOrEmpty_returnsUnknown() {
        BridgeWorkspace ws = workspace();

        assertEquals("unknown", ws.sanitize(null));
        assertEquals("unknown", ws.sanitize(""));
    }
}
