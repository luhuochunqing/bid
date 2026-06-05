// Input: stage transition cases
// Output: JUnit5 assertions covering linear happy path + illegal-jump matrix + CLOSED terminal + sub-stage linearity
// Pos: backend test source - pure JUnit5, no Spring
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStageTransitionPolicyTest {

    private static final ProjectStageTransitionPolicy.GateInputs GATE =
            ProjectStageTransitionPolicy.GateInputs.EMPTY;

    @Test
    void linearHappyPath_allAllowed() {
        ProjectStage[] order = {
                ProjectStage.INITIATED,
                ProjectStage.DRAFTING,
                ProjectStage.EVALUATING,
                ProjectStage.RESULT_PENDING,
                ProjectStage.RETROSPECTIVE,
                ProjectStage.CLOSED
        };
        for (int i = 0; i < order.length - 1; i++) {
            var d = ProjectStageTransitionPolicy.decide(order[i], order[i + 1], GATE);
            assertTrue(d.allowed(), "应允许 " + order[i] + "→" + order[i + 1] + "，实际：" + d);
        }
    }

    @ParameterizedTest
    @MethodSource("illegalPairs")
    void illegalJumps_allDenied(ProjectStage from, ProjectStage to) {
        var d = ProjectStageTransitionPolicy.decide(from, to, GATE);
        assertFalse(d.allowed(), "应拒绝 " + from + "→" + to);
        assertInstanceOf(ProjectStageTransitionPolicy.Decision.Deny.class, d);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> illegalPairs() {
        List<org.junit.jupiter.params.provider.Arguments> args = new ArrayList<>();
        ProjectStage[] all = ProjectStage.values();
        for (ProjectStage f : all) {
            for (ProjectStage t : all) {
                if (isLegalLinear(f, t)) continue;
                args.add(org.junit.jupiter.params.provider.Arguments.of(f, t));
            }
        }
        return args.stream();
    }

    private static boolean isLegalLinear(ProjectStage from, ProjectStage to) {
        return switch (from) {
            case INITIATED -> to == ProjectStage.DRAFTING;
            case DRAFTING -> to == ProjectStage.EVALUATING;
            case EVALUATING -> to == ProjectStage.RESULT_PENDING;
            case RESULT_PENDING -> to == ProjectStage.RETROSPECTIVE || to == ProjectStage.CLOSED;
            case RETROSPECTIVE -> to == ProjectStage.CLOSED;
            case CLOSED -> false;
        };
    }

    @Test
    void closedIsTerminal_anyOutgoingDenied() {
        for (ProjectStage to : ProjectStage.values()) {
            var d = ProjectStageTransitionPolicy.decide(ProjectStage.CLOSED, to, GATE);
            assertFalse(d.allowed(), "CLOSED 出向 " + to + " 应被拒");
        }
    }

    @Test
    void selfTransitionDenied() {
        for (ProjectStage s : ProjectStage.values()) {
            var d = ProjectStageTransitionPolicy.decide(s, s, GATE);
            assertFalse(d.allowed(), "同态自循环 " + s + " 应被拒");
        }
    }

    @Test
    void nullArguments_denied() {
        assertFalse(ProjectStageTransitionPolicy.decide(null, ProjectStage.DRAFTING, GATE).allowed());
        assertFalse(ProjectStageTransitionPolicy.decide(ProjectStage.INITIATED, null, GATE).allowed());
    }

    @Test
    void evaluationSubStage_linearHappy() {
        // 蓝图 V1.1 §4.3: 子状态序列 IN_PROGRESS → AWAITING_BOARD → RESULT_OUT → ANNOUNCED
        assertTrue(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.IN_PROGRESS, EvaluationSubStage.AWAITING_BOARD).allowed());
        assertTrue(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.AWAITING_BOARD, EvaluationSubStage.RESULT_OUT).allowed());
        assertTrue(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.RESULT_OUT, EvaluationSubStage.ANNOUNCED).allowed());
    }

    @Test
    void evaluationSubStage_illegalDenied() {
        // 跳级拒绝
        assertFalse(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.IN_PROGRESS, EvaluationSubStage.ANNOUNCED).allowed());
        assertFalse(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.AWAITING_BOARD, EvaluationSubStage.ANNOUNCED).allowed());
        // 倒退拒绝
        assertFalse(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.AWAITING_BOARD, EvaluationSubStage.IN_PROGRESS).allowed());
        assertFalse(ProjectStageTransitionPolicy.decideEvaluationSub(
                EvaluationSubStage.ANNOUNCED, EvaluationSubStage.RESULT_OUT).allowed());
        // ANNOUNCED 终态：所有出向均拒绝
        for (EvaluationSubStage s : EvaluationSubStage.values()) {
            assertFalse(ProjectStageTransitionPolicy.decideEvaluationSub(
                    EvaluationSubStage.ANNOUNCED, s).allowed());
        }
    }

    // ============ V1.1 新增：decideResultNext（按结果类型分流） ============

    @Test
    void decideResultNext_failed_goesToClosed() {
        assertEquals(ProjectStage.CLOSED,
                ProjectStageTransitionPolicy.decideResultNext(BidResultType.FAILED));
    }

    @Test
    void decideResultNext_abandoned_goesToClosed() {
        assertEquals(ProjectStage.CLOSED,
                ProjectStageTransitionPolicy.decideResultNext(BidResultType.ABANDONED));
    }

    @Test
    void decideResultNext_won_goesToRetrospective() {
        assertEquals(ProjectStage.RETROSPECTIVE,
                ProjectStageTransitionPolicy.decideResultNext(BidResultType.WON));
    }

    @Test
    void decideResultNext_lost_goesToRetrospective() {
        assertEquals(ProjectStage.RETROSPECTIVE,
                ProjectStageTransitionPolicy.decideResultNext(BidResultType.LOST));
    }

    @Test
    void decideResultNext_nullThrows() {
        assertThrows(NullPointerException.class,
                () -> ProjectStageTransitionPolicy.decideResultNext(null));
    }
}
