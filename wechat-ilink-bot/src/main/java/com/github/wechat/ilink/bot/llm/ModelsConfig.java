package com.github.wechat.ilink.bot.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.bot.config.LlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一模型/Provider 注册表。
 *
 * <p>设计目标：把模型、endpoint、token 收敛到一处统一管理，取代此前分散在 llm-config.json / task-config.json 里的模型字段。
 * {@code providers} 定义共享的 baseUrl + apiKey（+ uploadsUrl），每个功能块（chat/review/bridge）
 * 只声明引用哪个 provider 以及自己的 model。DashScope 这套公共值因此只定义一次，
 * Review（视频点评）与 Claude Bridge 共用。
 *
 * <p>取值路径：
 * <ul>
 *   <li>Chat —— {@code providers[chat.provider]} + {@code chat.model}，经 {@link #resolveChatLlmConfig()} 产出 {@link LlmConfig}</li>
 *   <li>Review —— {@code providers.dashscope} + {@code review.model}</li>
 *   <li>Bridge —— {@code providers.dashscope} + {@code bridge.model}</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelsConfig {

    private static final Logger log = LoggerFactory.getLogger(ModelsConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String DASHSCOPE = "dashscope";
    public static final String ZHIPU = "zhipu";

    private static final String DEFAULT_DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_DASHSCOPE_UPLOADS_URL = "https://dashscope.aliyuncs.com/api/v1/uploads";

    private Map<String, Provider> providers = new LinkedHashMap<String, Provider>();
    private Chat chat = new Chat();
    private FeatureModel review = new FeatureModel();
    private FeatureModel bridge = new FeatureModel();

    public static ModelsConfig load(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            try {
                ModelsConfig config = MAPPER.readValue(file, ModelsConfig.class);
                log.info("模型配置已加载: providers={}, chat.model={}, review.model={}, bridge.model={}",
                        config.providers.keySet(), config.chat.getModel(),
                        config.reviewModel(), config.bridgeModel());
                return config;
            } catch (IOException e) {
                log.error("模型配置读取失败: {}", filePath, e);
                return new ModelsConfig();
            }
        }

        ModelsConfig template = defaultTemplate();
        writeTemplate(file, template);
        log.info("模型未配置：{} 未找到，已生成模板", filePath);
        return template;
    }

    private static ModelsConfig defaultTemplate() {
        ModelsConfig config = new ModelsConfig();
        Provider zhipu = new Provider();
        zhipu.setBaseUrl("https://open.bigmodel.cn/api/coding/paas/v4");
        zhipu.setApiKey("your-zhipu-key-here");
        config.providers.put(ZHIPU, zhipu);

        Provider ds = new Provider();
        ds.setBaseUrl(DEFAULT_DASHSCOPE_BASE_URL);
        ds.setApiKey("your-dashscope-key-here");
        ds.setUploadsUrl(DEFAULT_DASHSCOPE_UPLOADS_URL);
        config.providers.put(DASHSCOPE, ds);

        config.chat.setProvider(ZHIPU);
        config.chat.setModel("glm-4-flash");
        config.review.setProvider(DASHSCOPE);
        config.review.setModel("qwen-omni");
        config.bridge.setProvider(DASHSCOPE);
        config.bridge.setModel("qwen-plus");
        config.bridge.setSmallModel("");
        return config;
    }

    private static void writeTemplate(File file, ModelsConfig config) {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            writer.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config));
            writer.close();
            log.info("已写入模型配置文件: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.warn("无法写入模型配置文件", e);
        }
    }

    /** 把 chat 块 + 其引用的 provider 解析成现成的 {@link LlmConfig}，下游消费方零改动。 */
    public LlmConfig resolveChatLlmConfig() {
        LlmConfig cfg = new LlmConfig();
        Provider p = provider(chat.getProvider());
        cfg.setProvider(isSet(chat.getType()) ? chat.getType() : "openai");
        if (p != null) {
            cfg.setApiKey(p.getApiKey());
            cfg.setBaseUrl(p.getBaseUrl());
        }
        cfg.setModel(chat.getModel());
        cfg.setMaxTokens(chat.getMaxTokens());
        cfg.setTimeoutMs(chat.getTimeoutMs());
        cfg.setMaxHistory(chat.getMaxHistory());
        cfg.setStreamingEnabled(chat.isStreamingEnabled());
        cfg.setTypingIntervalMs(chat.getTypingIntervalMs());
        return cfg;
    }

    public Provider provider(String name) {
        return name == null ? null : providers.get(name);
    }

    /** Review/Bridge 共用的 DashScope provider；缺失则返回空 Provider，避免 NPE。 */
    public Provider dashscope() {
        Provider p = provider(DASHSCOPE);
        return p == null ? new Provider() : p;
    }

    public String reviewModel() {
        return review == null ? null : review.getModel();
    }

    public String bridgeModel() {
        return bridge == null ? null : bridge.getModel();
    }

    public String bridgeSmallModel() {
        return bridge == null ? null : bridge.getSmallModel();
    }

    /** Bridge 声明引用的 provider（claude 子进程需要 Anthropic 协议端点）；未声明/未找到返回 null。 */
    public Provider bridgeProvider() {
        return bridge == null ? null : provider(bridge.getProvider());
    }

    public Map<String, Provider> getProviders() { return providers; }
    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers == null ? new LinkedHashMap<String, Provider>() : providers;
    }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat == null ? new Chat() : chat; }
    public FeatureModel getReview() { return review; }
    public void setReview(FeatureModel review) { this.review = review == null ? new FeatureModel() : review; }
    public FeatureModel getBridge() { return bridge; }
    public void setBridge(FeatureModel bridge) { this.bridge = bridge == null ? new FeatureModel() : bridge; }

    private static boolean isSet(String value) {
        return value != null && !value.isEmpty();
    }

    /** 共享 provider：endpoint + 凭证（+ 视频上传专用 URL）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Provider {
        private String baseUrl = "";
        private String apiKey = "";
        private String uploadsUrl = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
        public String getUploadsUrl() { return uploadsUrl; }
        public void setUploadsUrl(String uploadsUrl) { this.uploadsUrl = uploadsUrl == null ? "" : uploadsUrl; }
    }

    /** Review/Bridge 功能块：引用 provider + 自己的 model（+ Bridge 可选的小快模型）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeatureModel {
        private String provider = "";
        private String model = "";
        // 小快模型（haiku 角色）：用于配额预检/标题生成等后台调用；为空时复用 model
        private String smallModel = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider == null ? "" : provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model == null ? "" : model; }
        public String getSmallModel() { return smallModel; }
        public void setSmallModel(String smallModel) { this.smallModel = smallModel == null ? "" : smallModel; }
    }

    /** Chat 功能块：除 provider/model 外携带对话专属旋钮，映射到 {@link LlmConfig}。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chat {
        private String provider = "";
        private String type = "openai";
        private String model = "";
        private int maxTokens = 5000;
        private int timeoutMs = 30000;
        private int maxHistory = 20;
        private boolean streamingEnabled = true;
        private int typingIntervalMs = 5000;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider == null ? "" : provider; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model == null ? "" : model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxHistory() { return maxHistory; }
        public void setMaxHistory(int maxHistory) { this.maxHistory = maxHistory; }
        public boolean isStreamingEnabled() { return streamingEnabled; }
        public void setStreamingEnabled(boolean streamingEnabled) { this.streamingEnabled = streamingEnabled; }
        public int getTypingIntervalMs() { return typingIntervalMs; }
        public void setTypingIntervalMs(int typingIntervalMs) { this.typingIntervalMs = typingIntervalMs; }
    }
}
