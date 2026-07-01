package com.github.wechat.ilink.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Hook 子系统开关配置（Phase H4）：控制各内置 hook 的启用/停用。
 *
 * <p>文件位置：{@code data/hooks-config.json}。缺省即生成模板并使用默认值（全部启用，保持既有行为）。
 * 把 {@code audit}/{@code rateLimit} 置 false 即可关停对应横切能力。</p>
 */
public class HookConfig {

    private static final Logger log = LoggerFactory.getLogger(HookConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean audit = true;
    private boolean rateLimit = true;

    public HookConfig() {
    }

    public static HookConfig load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            createTemplate(file);
            log.info("Hook 配置未找到，已生成模板，使用默认值: {}", filePath);
            return new HookConfig();
        }
        try {
            HookConfig config = MAPPER.readValue(file, HookConfig.class);
            log.info("Hook 配置已加载: audit={}, rateLimit={}", config.isAudit(), config.isRateLimit());
            return config;
        } catch (IOException e) {
            log.error("Hook 配置读取失败: {}", filePath, e);
            return new HookConfig();
        }
    }

    private static void createTemplate(File file) {
        try {
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(new HookConfig()));
            writer.close();
            log.info("已创建 Hook 配置模板: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.warn("无法创建 Hook 配置模板", e);
        }
    }

    public boolean isAudit() {
        return audit;
    }

    public void setAudit(boolean audit) {
        this.audit = audit;
    }

    public boolean isRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(boolean rateLimit) {
        this.rateLimit = rateLimit;
    }
}
