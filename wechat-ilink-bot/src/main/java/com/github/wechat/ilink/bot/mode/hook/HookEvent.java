package com.github.wechat.ilink.bot.mode.hook;

/**
 * Bot 运行时 harness 的生命周期事件。
 *
 * <p>对标 Claude Code 的 PreToolUse/PostToolUse/Stop 等——harness 在这些点位触发已注册的
 * {@link BotHook}，把审计/限流/错误兜底/指标等横切关注点从主流程解耦。点位对应
 * {@code ModeRouter.route()} 的实际流程：</p>
 *
 * <ul>
 *   <li>{@link #ON_MESSAGE_RECEIVED} —— 入口门控：{@code touchActivity} 之后、路由之前（每条消息）。限流在此。</li>
 *   <li>{@link #ON_TEXT_RECEIVED} —— {@code extractText} 非空之后、前缀路由之前。入向审计在此。</li>
 *   <li>{@link #PRE_DISPATCH} / {@link #POST_DISPATCH} —— {@code current.handleText} 前后。</li>
 *   <li>{@link #PRE_SEND} —— 每个 {@code ModeSender.sendXxx} 之前。出向审计在此。</li>
 *   <li>{@link #ON_MODE_SWITCH} —— {@code /mode} 切换前后。</li>
 *   <li>{@link #ON_ERROR} —— {@code GameBot.onMessages} catch-all。</li>
 *   <li>{@link #ON_TURN_COMPLETE} —— 单条消息回合结束。</li>
 *   <li>{@link #ON_STARTUP} / {@link #ON_SHUTDOWN} —— 应用启停。</li>
 * </ul>
 *
 * <p><b>预留仪器点</b>：除 {@link #ON_MESSAGE_RECEIVED}/{@link #ON_TEXT_RECEIVED}/{@link #PRE_SEND} 有内置订阅者
 * （限流 / 入向审计 / 出向审计）外，其余事件（{@link #PRE_DISPATCH}/{@link #POST_DISPATCH}/
 * {@link #ON_MODE_SWITCH}/{@link #ON_ERROR}/{@link #ON_TURN_COMPLETE}/{@link #ON_STARTUP}/{@link #ON_SHUTDOWN}）
 * 已在主流程接线、但默认无订阅者——它们是为指标 / 告警 / 门控等未来横切需求预留的触发点，按需注册即可，
 * 不属于投机死代码。</p>
 */
public enum HookEvent {
    ON_MESSAGE_RECEIVED,
    ON_TEXT_RECEIVED,
    PRE_DISPATCH,
    POST_DISPATCH,
    PRE_SEND,
    ON_MODE_SWITCH,
    ON_ERROR,
    ON_TURN_COMPLETE,
    ON_STARTUP,
    ON_SHUTDOWN
}
