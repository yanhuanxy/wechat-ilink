package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.mode.claude.BridgeFileBuffer;
import com.github.wechat.ilink.bot.mode.claude.BridgeWorkspace;
import com.github.wechat.ilink.bot.mode.claude.ClaudeAdapterCallback;
import com.github.wechat.ilink.bot.mode.claude.ClaudeCodeAdapter;
import com.github.wechat.ilink.bot.mode.claude.ClaudeSession;
import com.github.wechat.ilink.bot.persistence.ClaudeSessionRepository;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.task.TaskMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaudeBridgeMode implements BotMode {

    private static final Logger log = LoggerFactory.getLogger(ClaudeBridgeMode.class);
    private static final int WECHAT_TEXT_LIMIT = 2000;
    private static final int TITLE_MAX_LEN = 40;
    private static final int MAX_OUTPUT_FILES = 10;
    private static final long MAX_OUTPUT_FILE_BYTES = 50L * 1024 * 1024;
    private static final String COMPACT_COMMAND = "/compact";
    private static final Set<String> IMAGE_EXTS = new HashSet<String>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));
    private static final Set<String> VIDEO_EXTS = new HashSet<String>(
            Arrays.asList("mp4", "mov", "avi", "mkv", "webm"));

    private final ClaudeCodeAdapter adapter;
    private final ClaudeSessionRepository repository;
    private final String cwd;
    private final String model;
    private final BridgeFileBuffer fileBuffer;
    private final BridgeWorkspace workspace;
    private final ExecutorService executor;
    // 自动压缩阈值：0=关闭；>0 表示每会话累计 N 轮后，下一条消息前自动 /compact。
    private final int compactThreshold;

    public ClaudeBridgeMode(ClaudeCodeAdapter adapter, ClaudeSessionRepository repository,
                            String cwd, String model) {
        this(adapter, repository, cwd, model, null, null, defaultExecutor(), 0);
    }

    public ClaudeBridgeMode(ClaudeCodeAdapter adapter, ClaudeSessionRepository repository,
                            String cwd, String model, ExecutorService executor) {
        this(adapter, repository, cwd, model, null, null, executor, 0);
    }

    public ClaudeBridgeMode(ClaudeCodeAdapter adapter, ClaudeSessionRepository repository,
                            String cwd, String model, BridgeFileBuffer fileBuffer, BridgeWorkspace workspace) {
        this(adapter, repository, cwd, model, fileBuffer, workspace, defaultExecutor(), 0);
    }

    public ClaudeBridgeMode(ClaudeCodeAdapter adapter, ClaudeSessionRepository repository,
                            String cwd, String model, BridgeFileBuffer fileBuffer, BridgeWorkspace workspace,
                            int compactThreshold) {
        this(adapter, repository, cwd, model, fileBuffer, workspace, defaultExecutor(), compactThreshold);
    }

    public ClaudeBridgeMode(ClaudeCodeAdapter adapter, ClaudeSessionRepository repository,
                            String cwd, String model, BridgeFileBuffer fileBuffer, BridgeWorkspace workspace,
                            ExecutorService executor) {
        this(adapter, repository, cwd, model, fileBuffer, workspace, executor, 0);
    }

    public ClaudeBridgeMode(ClaudeCodeAdapter adapter, ClaudeSessionRepository repository,
                            String cwd, String model, BridgeFileBuffer fileBuffer, BridgeWorkspace workspace,
                            ExecutorService executor, int compactThreshold) {
        this.adapter = adapter;
        this.repository = repository;
        this.cwd = cwd;
        this.model = model;
        this.fileBuffer = fileBuffer;
        this.workspace = workspace;
        this.executor = executor;
        this.compactThreshold = compactThreshold;
    }

    @Override
    public BotModeType type() {
        return BotModeType.CLAUDE;
    }

    /** 入向文件落到 60s 缓冲，等待下一条文字说明触发处理。由 ModeRouter 在 CLAUDE 模式下调用。 */
    public ModeOutcome bufferIncomingFile(ModeContext ctx, PlayerSession session,
                                          byte[] bytes, String fileName, boolean image) {
        String userId = session.getUserId();
        if (fileBuffer == null) {
            sendSafe(ctx, userId, "文件功能未启用，请在 task-config.json 中开启 Claude Bridge 后重试。");
            return ModeOutcome.handled();
        }
        BridgeFileBuffer.PutResult result = fileBuffer.put(userId, bytes, fileName, image);
        if (result.isAccepted()) {
            sendSafe(ctx, userId, "📎 已收到文件，请在 60 秒内发送处理说明（无需任何前缀）");
        } else {
            sendSafe(ctx, userId, "文件接收失败：" + result.getErrorMessage());
        }
        return ModeOutcome.handled();
    }

    @Override
    public ModeOutcome handleText(final ModeContext ctx, final PlayerSession session, final String text) {
        final String userId = session.getUserId();
        if (adapter == null) {
            sendSafe(ctx, userId, "Claude Bridge 未启用，请在 task-config.json 中开启后重试。");
            return ModeOutcome.handled();
        }

        final String resumeSessionId = session.getActiveClaudeSessionId();
        final boolean isNew = (resumeSessionId == null || resumeSessionId.isEmpty());
        final boolean privileged = session.isClaudePrivileged();
        final boolean plan = session.isClaudePlanMode();
        // /approve 置位的一次性执行指令：拼"执行上一轮计划"前缀后立即消费，避免重复触发。
        final boolean approved = session.isClaudeApprovedExec();
        if (approved) {
            session.setClaudeApprovedExec(false);
        }
        final String effectiveText = approved ? "请执行上一轮提出的计划。\n\n" + text : text;
        final BridgeFileBuffer.FileTicket ticket = (fileBuffer != null) ? fileBuffer.consume(userId) : null;
        startTypingSafe(ctx, userId);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                runClaude(ctx, session, userId, effectiveText, resumeSessionId, isNew, privileged, plan, ticket);
            }
        });
        return ModeOutcome.handled();
    }

    private void runClaude(final ModeContext ctx, final PlayerSession session, final String userId,
                           final String prompt, final String resumeSessionId, final boolean isNew,
                           final boolean privileged, final boolean plan, final BridgeFileBuffer.FileTicket ticket) {
        final ModeSender sender = ctx.sender();
        String effectivePrompt = prompt;
        Path outputDir = null;
        if (ticket != null && workspace != null) {
            try {
                Path input = workspace.writeInput(userId, ticket.getBytes(), ticket.getFileName());
                outputDir = workspace.freshOutputDir(userId);
                // 受限模式纯只读：Claude 无法写产物文件，故不告知 output 目录；提权后才提示回传。
                effectivePrompt = augmentPrompt(prompt, input, privileged ? outputDir : null);
            } catch (IOException e) {
                log.error("入向文件准备失败, userId={}", userId, e);
                sendSafe(ctx, userId, "文件准备失败：" + safeMessage(e));
                stopTypingSafe(ctx, userId);
                return;
            }
        }
        final Path outDir = outputDir;
        ClaudeAdapterCallback callback = new ClaudeAdapterCallback() {
            @Override
            public void onSessionId(String sessionId) {
                persistSession(userId, sessionId, prompt, isNew);
                session.setActiveClaudeSessionId(sessionId);
            }

            @Override
            public void onToken(String token) {
                startTypingSafe(ctx, userId);
            }

            @Override
            public void onComplete(String fullResponse) {
                try {
                    if (fullResponse == null || fullResponse.isEmpty()) {
                        sender.sendText(userId, "Claude 没有返回内容。");
                    } else {
                        for (String chunk : TaskMessageHandler.splitMessage(fullResponse, WECHAT_TEXT_LIMIT)) {
                            sender.sendText(userId, chunk);
                        }
                    }
                    if (outDir != null) {
                        sendOutputFiles(sender, userId, outDir);
                    }
                    session.incrementClaudeTurn();
                } catch (Exception e) {
                    log.error("发送 Claude 回复失败, userId={}", userId, e);
                } finally {
                    stopTypingSafe(ctx, userId);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Claude Bridge 执行失败, userId={}", userId, t);
                sendSafe(ctx, userId, "Claude 暂时无法回复：" + safeMessage(t));
                stopTypingSafe(ctx, userId);
            }
        };
        maybeCompact(ctx, session, userId, resumeSessionId, isNew, privileged, plan);
        adapter.run(userId, effectivePrompt, resumeSessionId, privileged, plan, callback);
    }

    /**
     * 达阈值时在处理用户消息前先跑一次 {@code /compact} 压缩上下文（adapter.run 阻塞，串行执行）。
     * 仅对续传中的会话生效；压缩失败仅记日志、不阻断后续回复。
     */
    private void maybeCompact(ModeContext ctx, PlayerSession session, String userId,
                              String resumeSessionId, boolean isNew, boolean privileged, boolean plan) {
        if (compactThreshold <= 0 || isNew
                || resumeSessionId == null || resumeSessionId.isEmpty()
                || session.getClaudeTurnCount() < compactThreshold) {
            return;
        }
        log.info("Claude Bridge 触发自动压缩, userId={}, turns={}, threshold={}",
                userId, session.getClaudeTurnCount(), compactThreshold);
        CompactCallback cc = new CompactCallback(userId);
        adapter.run(userId, COMPACT_COMMAND, resumeSessionId, privileged, plan, cc);
        if (cc.isSucceeded()) {
            session.resetClaudeTurnCount();
            sendSafe(ctx, userId, "🗜️ 上下文较长，已自动压缩历史对话。");
        } else {
            log.warn("Claude Bridge 自动压缩未成功（继续正常回复）, userId={}", userId);
        }
    }

    /** 压缩内部轮专用回调：不向用户透传摘要，仅记录成败用于决定是否重置轮次计数。 */
    private static final class CompactCallback implements ClaudeAdapterCallback {
        private final String userId;
        private volatile boolean succeeded;

        CompactCallback(String userId) {
            this.userId = userId;
        }

        boolean isSucceeded() {
            return succeeded;
        }

        @Override
        public void onSessionId(String sessionId) {
        }

        @Override
        public void onToken(String token) {
        }

        @Override
        public void onComplete(String fullResponse) {
            succeeded = true;
        }

        @Override
        public void onError(Throwable t) {
            log.warn("Claude Bridge /compact 执行失败, userId={}", userId, t);
        }
    }

    private void persistSession(String userId, String sessionId, String prompt, boolean isNew) {
        long now = System.currentTimeMillis();
        if (isNew) {
            repository.insert(new ClaudeSession(sessionId, userId, cwd, model, title(prompt), now, now));
        } else {
            repository.touchUpdatedAt(sessionId, now);
        }
    }

    private static String augmentPrompt(String text, Path input, Path output) {
        String result = text
                + "\n\n[附件] 用户上传的文件已保存到：" + input.toAbsolutePath();
        if (output != null) {
            result += "\n如需向用户回传文件，请将产物写入目录：" + output.toAbsolutePath();
        }
        return result;
    }

    private void sendOutputFiles(ModeSender sender, String userId, Path outputDir) {
        List<Path> files;
        try {
            files = workspace.collectOutputs(outputDir);
        } catch (IOException e) {
            log.error("扫描 output 目录失败, userId={}", userId, e);
            return;
        }
        int sent = 0;
        for (Path p : files) {
            if (sent >= MAX_OUTPUT_FILES) {
                log.warn("output 文件超过上限 {}，其余跳过, userId={}", MAX_OUTPUT_FILES, userId);
                break;
            }
            try {
                byte[] bytes = Files.readAllBytes(p);
                if (bytes.length > MAX_OUTPUT_FILE_BYTES) {
                    log.warn("output 文件过大跳过: {} ({} bytes)", p.getFileName(), bytes.length);
                    continue;
                }
                sendOneFile(sender, userId, p.getFileName().toString(), bytes);
                sent++;
            } catch (Exception e) {
                log.error("回发 output 文件失败: {}, userId={}", p.getFileName(), userId, e);
            }
        }
    }

    private void sendOneFile(ModeSender sender, String userId, String name, byte[] bytes) throws IOException {
        String ext = extension(name);
        if (IMAGE_EXTS.contains(ext)) {
            sender.sendImage(userId, bytes, name, "");
        } else if (VIDEO_EXTS.contains(ext)) {
            sender.sendVideo(userId, bytes, name, null, "");
        } else {
            sender.sendFile(userId, bytes, name, "");
        }
    }

    private static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return (dot < 0 || dot == name.length() - 1) ? "" : name.substring(dot + 1).toLowerCase();
    }

    private static String title(String prompt) {
        if (prompt == null) {
            return "";
        }
        String oneLine = prompt.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > TITLE_MAX_LEN ? oneLine.substring(0, TITLE_MAX_LEN) : oneLine;
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private void startTypingSafe(ModeContext ctx, String userId) {
        try {
            ctx.sender().startTyping(userId);
        } catch (Exception e) {
            log.warn("启动 typing 指示器失败, userId={}", userId, e);
        }
    }

    private void stopTypingSafe(ModeContext ctx, String userId) {
        try {
            ctx.sender().stopTyping(userId);
        } catch (Exception e) {
            log.warn("停止 typing 指示器失败, userId={}", userId, e);
        }
    }

    private void sendSafe(ModeContext ctx, String userId, String text) {
        try {
            ctx.sender().sendText(userId, text);
        } catch (Exception e) {
            log.error("发送消息失败, userId={}", userId, e);
        }
    }

    private static ExecutorService defaultExecutor() {
        return Executors.newFixedThreadPool(2, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "claude-bridge-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }
}
