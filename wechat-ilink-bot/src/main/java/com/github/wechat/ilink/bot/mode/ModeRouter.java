package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.mode.hook.HookContext;
import com.github.wechat.ilink.bot.mode.hook.HookEvent;
import com.github.wechat.ilink.bot.mode.hook.HookVerdict;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.session.SessionManager;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VideoItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ModeRouter {

    private static final Logger log = LoggerFactory.getLogger(ModeRouter.class);

    private final ModeContext ctx;
    private final ChatMode chatMode;
    private final FarmMode farmMode;
    private final SystemCommandMode systemMode;
    private final ReviewMode reviewMode;
    private final ClaudeBridgeMode claudeMode;
    private final AutogameMode autogameMode;
    private final Map<BotModeType, BotMode> switchableModes;

    public ModeRouter(ModeContext ctx,
                      ChatMode chatMode,
                      FarmMode farmMode,
                      SystemCommandMode systemMode,
                      ReviewMode reviewMode,
                      ClaudeBridgeMode claudeMode) {
        this(ctx, chatMode, farmMode, systemMode, reviewMode, claudeMode, null);
    }

    public ModeRouter(ModeContext ctx,
                      ChatMode chatMode,
                      FarmMode farmMode,
                      SystemCommandMode systemMode,
                      ReviewMode reviewMode,
                      ClaudeBridgeMode claudeMode,
                      AutogameMode autogameMode) {
        this.ctx = ctx;
        this.chatMode = chatMode;
        this.farmMode = farmMode;
        this.systemMode = systemMode;
        this.reviewMode = reviewMode;
        this.claudeMode = claudeMode;
        this.autogameMode = autogameMode;
        this.switchableModes = new HashMap<BotModeType, BotMode>();
        this.switchableModes.put(BotModeType.CHAT, chatMode);
        if (claudeMode != null) {
            this.switchableModes.put(BotModeType.CLAUDE, claudeMode);
        }
        if (autogameMode != null) {
            this.switchableModes.put(BotModeType.AUTOGAME, autogameMode);
        }
    }

    public ModeOutcome route(WeixinMessage msg) {
        String userId = msg.getFrom_user_id();
        if (userId == null || userId.isEmpty()) {
            return ModeOutcome.skip();
        }

        SessionManager sessions = ctx.sessions();
        PlayerSession session = sessions.getOrCreate(userId);
        session.touchActivity();

        if (ctx.hooks().has(HookEvent.ON_MESSAGE_RECEIVED)) {
            HookVerdict verdict = ctx.hooks().fire(HookEvent.ON_MESSAGE_RECEIVED,
                    HookContext.builder().userId(userId).session(session).build());
            if (verdict.isShortCircuit()) {
                return verdict.getOutcome();
            }
        }

        if (findVideoItem(msg) != null) {
            return reviewMode.handleVideo(ctx, session, msg);
        }

        if (claudeMode != null && session.getCurrentMode() == BotModeType.CLAUDE) {
            MessageItem media = findMediaItem(msg);
            if (media != null) {
                log.info("CLAUDE 媒体入向命中, userId={}, mode={}", userId, session.getCurrentMode());
                return handleClaudeFileIntake(ctx, session, media);
            }
        }

        String text = extractText(msg);
        if (text == null || text.isEmpty()) {
            return ModeOutcome.skip();
        }

        if (ctx.hooks().has(HookEvent.ON_TEXT_RECEIVED)) {
            ctx.hooks().fire(HookEvent.ON_TEXT_RECEIVED,
                    HookContext.builder().userId(userId).text(text).session(session).build());
        }

        if (text.startsWith("#")) {
            return farmMode.handleText(ctx, session, text);
        }

        if (text.startsWith("/")) {
            return systemMode.handleText(ctx, session, text);
        }

        if (text.startsWith("!") && autogameMode != null) {
            return autogameMode.handleText(ctx, session, text);
        }

        log.debug("评测 pending 检查, userId={}, mode={}", userId, session.getCurrentMode());
        ModeOutcome pending = reviewMode.handlePendingPrompt(ctx, session, text);
        if (pending.isHandled()) {
            return pending;
        }

        BotMode current = switchableModes.get(session.getCurrentMode());
        if (current == null) {
            log.warn("用户 {} 的当前模式 {} 未注册，回退到 CHAT", userId, session.getCurrentMode());
            current = chatMode;
        }
        if (ctx.hooks().has(HookEvent.PRE_DISPATCH)) {
            HookVerdict pre = ctx.hooks().fire(HookEvent.PRE_DISPATCH,
                    HookContext.builder().userId(userId).text(text).session(session)
                            .targetMode(current.type()).build());
            if (pre.isShortCircuit()) {
                return pre.getOutcome();
            }
            if (pre.isBlock()) {
                sendSafe(ctx, userId, pre.getReason());
                return ModeOutcome.handled();
            }
        }
        long dispatchStart = System.currentTimeMillis();
        log.info("分发到当前模式, userId={}, mode={}", userId, session.getCurrentMode());
        ModeOutcome outcome = current.handleText(ctx, session, text);
        if (ctx.hooks().has(HookEvent.POST_DISPATCH)) {
            ctx.hooks().fire(HookEvent.POST_DISPATCH,
                    HookContext.builder().userId(userId).text(text).session(session)
                            .targetMode(current.type()).outcome(outcome)
                            .durationMs(System.currentTimeMillis() - dispatchStart).build());
        }
        return outcome;
    }

    static String extractText(WeixinMessage msg) {
        if (msg.getItem_list() == null) {
            return null;
        }
        for (MessageItem item : msg.getItem_list()) {
            if (item.getText_item() != null) {
                return item.getText_item().getText();
            }
        }
        return null;
    }

    private ModeOutcome handleClaudeFileIntake(ModeContext ctx, PlayerSession session, MessageItem item) {
        boolean isImage = item.getImage_item() != null;
        String userId = session.getUserId();
        try {
            byte[] bytes = isImage ? ctx.downloader().downloadImage(item)
                    : ctx.downloader().downloadFile(item);
            String fileName = resolveFileName(item, isImage);
            return claudeMode.bufferIncomingFile(ctx, session, bytes, fileName, isImage);
        } catch (IOException e) {
            log.error("入向文件下载失败, userId={}", userId, e);
            sendSafe(ctx, userId, "文件下载失败，请重试");
            return ModeOutcome.handled();
        }
    }

    private void sendSafe(ModeContext ctx, String userId, String text) {
        try {
            ctx.sender().sendText(userId, text);
        } catch (Exception e) {
            log.error("发送消息失败, userId={}", userId, e);
        }
    }

    private static String resolveFileName(MessageItem item, boolean isImage) {
        if (!isImage && item.getFile_item() != null && item.getFile_item().getFile_name() != null
                && !item.getFile_item().getFile_name().isEmpty()) {
            return item.getFile_item().getFile_name();
        }
        return "input_" + System.currentTimeMillis() + (isImage ? ".jpg" : ".bin");
    }

    static MessageItem findMediaItem(WeixinMessage msg) {
        if (msg.getItem_list() == null) {
            return null;
        }
        for (MessageItem item : msg.getItem_list()) {
            if (item.getImage_item() != null || item.getFile_item() != null) {
                return item;
            }
        }
        return null;
    }

    static MessageItem findVideoItem(WeixinMessage msg) {
        if (msg.getItem_list() == null) {
            return null;
        }
        for (MessageItem item : msg.getItem_list()) {
            VideoItem v = item.getVideo_item();
            if (v != null) {
                return item;
            }
        }
        return null;
    }
}
