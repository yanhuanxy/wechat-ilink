package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.util.MessageAuditLog;

/**
 * 入向审计 hook（{@link HookEvent#ON_TEXT_RECEIVED}）：把"用户 → bot"的文本消息落地审计日志。
 *
 * <p>由 {@code ModeRouter} 在 {@code extractText} 非空、前缀路由之前触发。包装既有
 * {@link MessageAuditLog#inbound}，行为不变（仅从硬编码迁为可插拔 hook）。</p>
 */
public class InboundAuditHook implements BotHook {

    @Override
    public HookEvent event() {
        return HookEvent.ON_TEXT_RECEIVED;
    }

    @Override
    public HookVerdict handle(HookContext ctx) {
        MessageAuditLog.inbound(ctx.getUserId(), ctx.getText());
        return HookVerdict.continue_();
    }
}
