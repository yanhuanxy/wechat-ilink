package com.github.wechat.ilink.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * autogame-xcx 远程服务配置：是否启用 + MCP server URL + 鉴权 token。
 *
 * 文件位置：data/autogame-config.json
 * 缺省 mcpUrl：http://localhost:8765（Python GUI 默认端口）
 * authToken 为空时 McpClient 不发送鉴权 header（本地开发场景兼容）
 */
public class AutogameConfig {

    private static final Logger log = LoggerFactory.getLogger(AutogameConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEFAULT_MCP_URL = "http://localhost:8765";

    private boolean enabled;
    private String mcpUrl;
    private String authToken;

    public AutogameConfig() {
        this.enabled = false;
        this.mcpUrl = DEFAULT_MCP_URL;
    }

    public static AutogameConfig load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            createTemplate(file);
            AutogameConfig config = new AutogameConfig();
            log.info("Autogame MCP 未启用：{} 未找到，已生成模板", filePath);
            return config;
        }
        try {
            AutogameConfig config = MAPPER.readValue(file, AutogameConfig.class);
            if (config.mcpUrl == null || config.mcpUrl.isEmpty()) {
                config.mcpUrl = DEFAULT_MCP_URL;
            }
            log.info("Autogame 配置已加载: enabled={}, mcpUrl={}, authTokenSet={}",
                    config.isEnabled(), config.getMcpUrl(), isSet(config.getAuthToken()));
            return config;
        } catch (IOException e) {
            log.error("Autogame 配置读取失败: {}", filePath, e);
            return new AutogameConfig();
        }
    }

    private static void createTemplate(File file) {
        try {
            file.getParentFile().mkdirs();
            AutogameConfig template = new AutogameConfig();
            template.setEnabled(false);
            FileWriter writer = new FileWriter(file);
            writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(template));
            writer.close();
            log.info("已创建 Autogame 配置模板: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.warn("无法创建 Autogame 配置模板", e);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMcpUrl() { return mcpUrl; }
    public void setMcpUrl(String mcpUrl) { this.mcpUrl = mcpUrl; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty();
    }
}
