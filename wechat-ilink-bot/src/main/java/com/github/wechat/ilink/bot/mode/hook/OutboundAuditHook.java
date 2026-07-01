package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.util.MessageAuditLog;

/**
 * 出向审计 hook（{@link HookEvent#PRE_SEND}）：把"bot → 用户"的发送内容落地审计日志。
 *
 * <p>由 {@code GameBot} 在每个 {@code sendXxx} 之前触发。包装既有 {@link MessageAuditLog#outbound}，行为不变。
 * {@code ctx.sendKind} = {@code text}/{@code image}/{@code file}/{@code video}；{@code ctx.text} = 内容摘要。</p>
 */
public class OutboundAuditHook implements BotHook {

    @Override
    public HookEvent event() {
        return HookEvent.PRE_SEND;
    }

    @Override
    public HookVerdict handle(HookContext ctx) {
        MessageAuditLog.outbound(ctx.getUserId(), ctx.getSendKind(), ctx.getText());
        return HookVerdict.continue_();
    }
}
