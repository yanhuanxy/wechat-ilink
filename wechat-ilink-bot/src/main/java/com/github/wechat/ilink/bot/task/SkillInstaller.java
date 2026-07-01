package com.github.wechat.ilink.bot.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从 classpath 把内置 skill 解压到 claude 子进程的工作目录，
 * 让 headless 模式下能发现并触发这些 skill。
 *
 * 仅在目标文件不存在时安装；用户后续对 rubric 等文件的调整不会被覆盖。
 * 升级内置版本时需要手动删除目标目录。
 */
public class SkillInstaller {

    private static final Logger log = LoggerFactory.getLogger(SkillInstaller.class);

    // {skillName, 相对路径}；新增 skill 或文件时在这里追加。
    // classpath 资源在 JAR 内不能用 File.listFiles 枚举，故显式列出。
    private static final String[][] BUNDLED_FILES = {
            {"piano-practice-review", "SKILL.md"},
            {"piano-practice-review", "references/rubrics/beginner.md"},
            {"piano-practice-review", "references/rubrics/intermediate.md"},
            {"piano-practice-review", "references/rubrics/exam-prep.md"},
            {"piano-practice-review", "scripts/analyze_video.py"},
    };

    private final Path claudeHome;

    public SkillInstaller(Path claudeHome) {
        this.claudeHome = claudeHome;
    }

    public void installAll() throws IOException {
        Path skillsRoot = claudeHome.resolve(".claude").resolve("skills");
        Files.createDirectories(skillsRoot);
        int installed = 0;
        int skipped = 0;
        int missing = 0;
        for (String[] entry : BUNDLED_FILES) {
            String skillName = entry[0];
            String relPath = entry[1];
            Path target = skillsRoot.resolve(skillName).resolve(relPath);
            if (Files.exists(target)) {
                skipped++;
                continue;
            }
            String resPath = "/skills/" + skillName + "/" + relPath;
            try (InputStream in = SkillInstaller.class.getResourceAsStream(resPath)) {
                if (in == null) {
                    log.warn("内置 skill 资源缺失: {}", resPath);
                    missing++;
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
                installed++;
            }
        }
        log.info("Skill 安装完成: claudeHome={}, 新装={}, 已存在跳过={}, 缺失={}",
                claudeHome, installed, skipped, missing);
    }
}
