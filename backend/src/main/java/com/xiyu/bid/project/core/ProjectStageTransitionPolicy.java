// Input: current stage, requested stage, gate inputs, result type
// Output: Allow / Deny(reason) sealed Decision; linear-only with result-aware routing
// Pos: project/core/ - pure rule, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.Objects;

/**
 * 项目阶段 FSM 决策。产品蓝图 V1.1 §4.3。
 * <p>
 * 规则：
 * <ul>
 *   <li>仅允许相邻阶段顺向推进（INITIATED→...→CLOSED）。</li>
 *   <li>CLOSED 为终态，任何离开均拒绝。</li>
 *   <li>跨级跳转、倒退、同态自循环均拒绝。</li>
 *   <li>结果确认阶段（RESULT_PENDING）按投标结果类型分流：
 *       中标/未中标 → 复盘；流标/弃标 → 结项。</li>
 * </ul>
 */
public final class ProjectStageTransitionPolicy {

    private ProjectStageTransitionPolicy() {
    }

    public static Decision decide(ProjectStage current, ProjectStage requested, GateInputs gateInputs) {
        if (current == null || requested == null) {
            return new Decision.Deny("current/requested stage 不能为空");
        }
        if (current.isTerminal()) {
            return new Decision.Deny("项目已结项，不可再次切换阶段");
        }
        if (current == requested) {
            return new Decision.Deny("不能切换到当前阶段（同态）");
        }
        ProjectStage expectedNext = next(current);
        // RESULT_PENDING → CLOSED 是合法的分流路径（流标/弃标），
        // 已在 decideResultNext() 中按结果类型路由，此处豁免线性检查。
        boolean validTransition = requested == expectedNext
                || (current == ProjectStage.RESULT_PENDING && requested == ProjectStage.CLOSED);
        if (expectedNext == null || !validTransition) {
            return new Decision.Deny("非法跳转：" + current + "→" + requested + "，仅允许线性顺推到 " + expectedNext);
        }
        Objects.requireNonNull(gateInputs, "gateInputs 不能为空");
        return Decision.ALLOW;
    }

    public static Decision decideEvaluationSub(EvaluationSubStage current, EvaluationSubStage requested) {
        if (current == null || requested == null) {
            return new Decision.Deny("evaluation sub-stage 不能为空");
        }
        if (current == requested) {
            return new Decision.Deny("不能切换到当前子状态");
        }
        EvaluationSubStage expected = nextSub(current);
        if (expected == null || requested != expected) {
            return new Decision.Deny("评标子状态非法跳转：" + current + "→" + requested);
        }
        return Decision.ALLOW;
    }

    private static ProjectStage next(ProjectStage s) {
        return switch (s) {
            case INITIATED -> ProjectStage.DRAFTING;
            case DRAFTING -> ProjectStage.EVALUATING;
            case EVALUATING -> ProjectStage.RESULT_PENDING;
            case RESULT_PENDING -> ProjectStage.RETROSPECTIVE;
            case RETROSPECTIVE -> ProjectStage.CLOSED;
            case CLOSED -> null;
        };
    }

    private static EvaluationSubStage nextSub(EvaluationSubStage s) {
        return switch (s) {
            case IN_PROGRESS -> EvaluationSubStage.AWAITING_BOARD;
            case AWAITING_BOARD -> EvaluationSubStage.RESULT_OUT;
            case RESULT_OUT -> EvaluationSubStage.ANNOUNCED;
            case ANNOUNCED -> null;
        };
    }

    /**
     * 结果确认阶段按投标结果类型决定下一状态。
     * 产品蓝图 V1.1 §4.3：中标/未中标 → 复盘；流标/弃标 → 结项。
     */
    public static ProjectStage decideResultNext(BidResultType resultType) {
        Objects.requireNonNull(resultType, "resultType 不能为空");
        return (resultType == BidResultType.FAILED || resultType == BidResultType.ABANDONED)
                ? ProjectStage.CLOSED
                : ProjectStage.RETROSPECTIVE;
    }

    /** Gate inputs 占位：未来由 shell 注入（保证金已退回、任务全完成等）。 */
    public record GateInputs(boolean allTasksCompleted, boolean depositReturnedOrNotRequired) {
        public static final GateInputs EMPTY = new GateInputs(false, false);
    }

    /** Sealed Decision: Allow | Deny{reason}. */
    public sealed interface Decision permits Decision.Allow, Decision.Deny {
        Decision ALLOW = new Allow();

        default boolean allowed() {
            return this instanceof Allow;
        }

        record Allow() implements Decision {
        }

        record Deny(String reason) implements Decision {
        }
    }
}
