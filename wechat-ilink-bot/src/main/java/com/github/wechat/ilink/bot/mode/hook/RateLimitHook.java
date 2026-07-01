package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.mode.ModeOutcome;
import com.github.wechat.ilink.bot.mode.ModeSender;
import com.github.wechat.ilink.bot.mode.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 入口限流 hook（{@link HookEvent#ON_MESSAGE_RECEIVED}）：per-user 固定窗口限流，超限发繁忙提示并 short-circuit。
 *
 * <p>包装既有 {@link RateLimiter}；由 {@code GameBot} 构造、{@code ModeRouter} 在路由入口触发。
 * 行为与原硬编码限流一致（{@code ModeRouter:85}）：超限则尽力发送繁忙提示、直接返回 {@link ModeOutcome#handled()}。</p>
 */
public class RateLimitHook implements BotHook {

    private static final Logger log = LoggerFactory.getLogger(RateLimitHook.class);
    private static final String BUSY_MESSAGE = "请求过于频繁，请稍后再试";

    private final RateLimiter rateLimiter;
    private final ModeSender sender;

    public RateLimitHook(RateLimiter rateLimiter, ModeSender sender) {
        this.rateLimiter = rateLimiter;
        this.sender = sender;
    }

    @Override
    public HookEvent event() {
        return HookEvent.ON_MESSAGE_RECEIVED;
    }

    @Override
    public HookVerdict handle(HookContext ctx) {
        String userId = ctx.getUserId();
        if (rateLimiter == null || userId == null || userId.isEmpty()) {
            return HookVerdict.continue_();
        }
        if (rateLimiter.tryAcquire(userId)) {
            return HookVerdict.continue_();
        }
        try {
            sender.sendText(userId, BUSY_MESSAGE);
        } catch (IOException e) {
            log.warn("限流繁忙提示发送失败, userId={}", userId, e);
        } catch (RuntimeException e) {
            log.warn("限流繁忙提示发送异常, userId={}", userId, e);
        }
        return HookVerdict.shortCircuit(ModeOutcome.handled());
    }
}
