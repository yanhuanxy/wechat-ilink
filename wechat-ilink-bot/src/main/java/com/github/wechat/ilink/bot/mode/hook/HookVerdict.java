package com.github.wechat.ilink.bot.mode.hook;

import com.github.wechat.ilink.bot.mode.ModeOutcome;

/**
 * hook 的控制流裁决，对标 Claude Code 的 exit code（0=放行 / 2=阻断）+ JSON stdout。
 *
 * <p>{@link HookRegistry#fire} 按 hook 注册顺序触发，遇到首个非 {@link Decision#CONTINUE} 即止。
 * 复用既有 {@link ModeOutcome} 表达"直接给定路由结局"，避免另造一套结局语义。</p>
 */
public final class HookVerdict {

    public enum Decision {
        CONTINUE,
        BLOCK,
        SHORT_CIRCUIT
    }

    private final Decision decision;
    private final String reason;
    private final ModeOutcome outcome;

    private HookVerdict(Decision decision, String reason, ModeOutcome outcome) {
        this.decision = decision;
        this.reason = reason;
        this.outcome = outcome;
    }

    /** 放行（≈ exit 0）。 */
    public static HookVerdict continue_() {
        return new HookVerdict(Decision.CONTINUE, null, null);
    }

    /** 阻断主流程（≈ exit 2），{@code reason} 供日志/反馈。 */
    public static HookVerdict block(String reason) {
        return new HookVerdict(Decision.BLOCK, reason, null);
    }

    /** 直接给定路由结局，跳过后续主流程（如限流直接返回 {@link ModeOutcome#handled()}）。 */
    public static HookVerdict shortCircuit(ModeOutcome outcome) {
        return new HookVerdict(Decision.SHORT_CIRCUIT, null, outcome);
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public ModeOutcome getOutcome() {
        return outcome;
    }

    public boolean isContinue() {
        return decision == Decision.CONTINUE;
    }

    public boolean isBlock() {
        return decision == Decision.BLOCK;
    }

    public boolean isShortCircuit() {
        return decision == Decision.SHORT_CIRCUIT;
    }
}
