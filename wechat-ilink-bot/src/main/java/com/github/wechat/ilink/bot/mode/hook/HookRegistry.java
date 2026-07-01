package com.github.wechat.ilink.bot.mode.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link HookEvent} 分组的 hook 注册表。
 *
 * <p>{@link #fire} 顺序触发某事件的全部 hook，遇到首个非 {@link HookVerdict.Decision#CONTINUE} 即止并返回该裁决；
 * 无订阅或全部放行则返回 {@link HookVerdict#continue_()}。单个 hook 抛 {@link RuntimeException}
 * 不影响主流程（记 WARN 后视作放行），避免审计/限流 hook 的 bug 拖垮消息处理。</p>
 */
public class HookRegistry {

    private static final Logger log = LoggerFactory.getLogger(HookRegistry.class);

    private final Map<HookEvent, List<BotHook>> hooks = new EnumMap<HookEvent, List<BotHook>>(HookEvent.class);

    /** 注册一个 hook（按调用顺序追加到其事件列表末尾）。null 静默忽略。 */
    public void register(BotHook hook) {
        if (hook == null) {
            return;
        }
        List<BotHook> list = hooks.get(hook.event());
        if (list == null) {
            list = new ArrayList<BotHook>();
            hooks.put(hook.event(), list);
        }
        list.add(hook);
    }

    /** 该事件是否有订阅 hook（无则调用方可跳过构造 {@link HookContext}）。 */
    public boolean has(HookEvent event) {
        List<BotHook> list = hooks.get(event);
        return list != null && !list.isEmpty();
    }

    /**
     * 顺序触发某事件的全部 hook，首个非 CONTINUE 即止。无订阅返回 {@link HookVerdict#continue_()}。
     * {@code ctx} 可为 null（仅放行型 hook 能正常工作）。
     */
    public HookVerdict fire(HookEvent event, HookContext ctx) {
        List<BotHook> list = hooks.get(event);
        if (list == null || list.isEmpty()) {
            return HookVerdict.continue_();
        }
        for (BotHook hook : list) {
            HookVerdict verdict;
            try {
                verdict = hook.handle(ctx);
            } catch (RuntimeException e) {
                log.warn("hook 执行异常, event={}, hook={}, 视作放行",
                        event, hook.getClass().getSimpleName(), e);
                continue;
            }
            if (!verdict.isContinue()) {
                return verdict;
            }
        }
        return HookVerdict.continue_();
    }
}
