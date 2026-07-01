package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.engine.GameEngine;
import com.github.wechat.ilink.bot.engine.ResponseRenderer;
import com.github.wechat.ilink.bot.llm.ChatHistoryManager;
import com.github.wechat.ilink.bot.llm.LlmProvider;
import com.github.wechat.ilink.bot.llm.LlmRequestQueue;
import com.github.wechat.ilink.bot.mcp.McpClient;
import com.github.wechat.ilink.bot.mcp.McpToolRegistry;
import com.github.wechat.ilink.bot.mode.hook.HookRegistry;
import com.github.wechat.ilink.bot.persistence.ClaudeSessionRepository;
import com.github.wechat.ilink.bot.session.SessionManager;
import com.github.wechat.ilink.bot.task.TaskMessageHandler;

/**
 * 各 {@link BotMode} 执行时持有的依赖快照（不可变）。
 *
 * <p>统一经 {@link Builder} 构造——字段多、多数测试仅设子集，fluent 构造比多参重叠构造器更稳。
 * 默认值：{@code mcpClient}/{@code mcpToolRegistry} 未设为 {@code null}；{@code hooks} 未设为空
 * {@link HookRegistry}（等价旧重载的默认空注册表，可继续 {@code register()}）。</p>
 */
public final class ModeContext {

    private final ModeSender sender;
    private final MediaDownloader downloader;
    private final GameEngine engine;
    private final ResponseRenderer renderer;
    private final LlmProvider llmProvider;
    private final ChatHistoryManager chatHistory;
    private final LlmRequestQueue llmQueue;
    private final SessionManager sessions;
    private final TaskMessageHandler taskHandler;
    private final ClaudeSessionRepository claudeSessionRepo;
    private final boolean streamingEnabled;
    private final int typingIntervalMs;
    private final McpClient mcpClient;
    private final McpToolRegistry mcpToolRegistry;
    private final HookRegistry hooks;

    private ModeContext(Builder b) {
        this.sender = b.sender;
        this.downloader = b.downloader;
        this.engine = b.engine;
        this.renderer = b.renderer;
        this.llmProvider = b.llmProvider;
        this.chatHistory = b.chatHistory;
        this.llmQueue = b.llmQueue;
        this.sessions = b.sessions;
        this.taskHandler = b.taskHandler;
        this.claudeSessionRepo = b.claudeSessionRepo;
        this.streamingEnabled = b.streamingEnabled;
        this.typingIntervalMs = b.typingIntervalMs;
        this.mcpClient = b.mcpClient;
        this.mcpToolRegistry = b.mcpToolRegistry;
        this.hooks = b.hooks;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModeSender sender() {
        return sender;
    }

    public MediaDownloader downloader() {
        return downloader;
    }

    public GameEngine engine() {
        return engine;
    }

    public ResponseRenderer renderer() {
        return renderer;
    }

    public LlmProvider llmProvider() {
        return llmProvider;
    }

    public ChatHistoryManager chatHistory() {
        return chatHistory;
    }

    public LlmRequestQueue llmQueue() {
        return llmQueue;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public TaskMessageHandler taskHandler() {
        return taskHandler;
    }

    public ClaudeSessionRepository claudeSessionRepo() {
        return claudeSessionRepo;
    }

    public boolean streamingEnabled() {
        return streamingEnabled;
    }

    public int typingIntervalMs() {
        return typingIntervalMs;
    }

    /** MCP 客户端；未启用时为 null。 */
    public McpClient mcpClient() {
        return mcpClient;
    }

    /** MCP tool 注册表；未启用时为 null。 */
    public McpToolRegistry mcpToolRegistry() {
        return mcpToolRegistry;
    }

    /** 生命周期 hook 注册表（{@code GameBot} 构造期注入；默认空注册表，不影响既有行为）。 */
    public HookRegistry hooks() {
        return hooks;
    }

    /** Fluent 构造器（与 {@code HookContext.Builder} 同风格）。字段全可选，按需设置。 */
    public static final class Builder {
        private ModeSender sender;
        private MediaDownloader downloader;
        private GameEngine engine;
        private ResponseRenderer renderer;
        private LlmProvider llmProvider;
        private ChatHistoryManager chatHistory;
        private LlmRequestQueue llmQueue;
        private SessionManager sessions;
        private TaskMessageHandler taskHandler;
        private ClaudeSessionRepository claudeSessionRepo;
        private boolean streamingEnabled;
        private int typingIntervalMs;
        private McpClient mcpClient;
        private McpToolRegistry mcpToolRegistry;
        private HookRegistry hooks = new HookRegistry();

        public Builder sender(ModeSender sender) {
            this.sender = sender;
            return this;
        }

        public Builder downloader(MediaDownloader downloader) {
            this.downloader = downloader;
            return this;
        }

        public Builder engine(GameEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder renderer(ResponseRenderer renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder llmProvider(LlmProvider llmProvider) {
            this.llmProvider = llmProvider;
            return this;
        }

        public Builder chatHistory(ChatHistoryManager chatHistory) {
            this.chatHistory = chatHistory;
            return this;
        }

        public Builder llmQueue(LlmRequestQueue llmQueue) {
            this.llmQueue = llmQueue;
            return this;
        }

        public Builder sessions(SessionManager sessions) {
            this.sessions = sessions;
            return this;
        }

        public Builder taskHandler(TaskMessageHandler taskHandler) {
            this.taskHandler = taskHandler;
            return this;
        }

        public Builder claudeSessionRepo(ClaudeSessionRepository claudeSessionRepo) {
            this.claudeSessionRepo = claudeSessionRepo;
            return this;
        }

        public Builder streamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
            return this;
        }

        public Builder typingIntervalMs(int typingIntervalMs) {
            this.typingIntervalMs = typingIntervalMs;
            return this;
        }

        public Builder mcpClient(McpClient mcpClient) {
            this.mcpClient = mcpClient;
            return this;
        }

        public Builder mcpToolRegistry(McpToolRegistry mcpToolRegistry) {
            this.mcpToolRegistry = mcpToolRegistry;
            return this;
        }

        public Builder hooks(HookRegistry hooks) {
            this.hooks = hooks;
            return this;
        }

        public ModeContext build() {
            return new ModeContext(this);
        }
    }
}
