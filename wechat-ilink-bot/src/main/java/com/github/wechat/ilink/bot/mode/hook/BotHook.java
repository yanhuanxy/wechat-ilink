package com.github.wechat.ilink.bot.mode.hook;

/**
 * Bot 运行时 harness 的生命周期 hook。
 *
 * <p>实现类声明关注的 {@link HookEvent}，在该事件被 {@link HookRegistry#fire} 触发时执行，
 * 并返回 {@link HookVerdict} 控制主流程。对标 Claude Code 在 {@code settings.json} 注册的
 * shell hook（matcher + command + exit code）。注册时机在组合根（{@code GameBot} 构造器）。</p>
 */
public interface BotHook {

    HookEvent event();

    HookVerdict handle(HookContext ctx);
}
