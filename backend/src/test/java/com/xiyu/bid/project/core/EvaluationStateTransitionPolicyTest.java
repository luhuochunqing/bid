// Input: 评标子状态转换案例
// Output: JUnit5 断言覆盖自由切换 happy + null/同态拒绝
// Pos: backend test source - 纯 JUnit5
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蓝图 V1.1 §4.3：四个评标子状态可自由切换，无顺序限制。
 * 仅 null 入参和同态自循环拒绝。
 */
class EvaluationStateTransitionPolicyTest {

    @Test
    void freeSwitching_allDistinctPairsAllowed() {
        EvaluationSubStage[] all = EvaluationSubStage.values();
        for (EvaluationSubStage from : all) {
            for (EvaluationSubStage to : all) {
                if (from == to) continue;
                assertTrue(EvaluationStateTransitionPolicy.decide(from, to).allowed(),
                        "蓝图要求自由切换: " + from + "→" + to);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(EvaluationSubStage.class)
    void selfTransitionDenied(EvaluationSubStage s) {
        var d = EvaluationStateTransitionPolicy.decide(s, s);
        assertFalse(d.allowed(), "不允许切换到当前子状态: " + s);
        assertInstanceOf(EvaluationStateTransitionPolicy.Decision.Deny.class, d);
    }

    @Test
    void nullArguments_denied() {
        assertFalse(EvaluationStateTransitionPolicy.decide(null, EvaluationSubStage.IN_PROGRESS).allowed());
        assertFalse(EvaluationStateTransitionPolicy.decide(EvaluationSubStage.IN_PROGRESS, null).allowed());
        assertFalse(EvaluationStateTransitionPolicy.decide(null, null).allowed());
    }

    @Test
    void announcedNotTerminal_canSwitchBack() {
        // 蓝图 V1.1: 新语义允许从 ANNOUNCED 切换回任意其他状态
        assertTrue(EvaluationStateTransitionPolicy.decide(
                EvaluationSubStage.ANNOUNCED, EvaluationSubStage.IN_PROGRESS).allowed());
        assertTrue(EvaluationStateTransitionPolicy.decide(
                EvaluationSubStage.ANNOUNCED, EvaluationSubStage.RESULT_OUT).allowed());
    }

    @Test
    void resultOutNewState_allTransitionsAllowed() {
        // RESULT_OUT 是 V1.1 新增子状态，所有与之的合法切换都应允许
        assertTrue(EvaluationStateTransitionPolicy.decide(
                EvaluationSubStage.IN_PROGRESS, EvaluationSubStage.RESULT_OUT).allowed());
        assertTrue(EvaluationStateTransitionPolicy.decide(
                EvaluationSubStage.RESULT_OUT, EvaluationSubStage.ANNOUNCED).allowed());
        assertTrue(EvaluationStateTransitionPolicy.decide(
                EvaluationSubStage.RESULT_OUT, EvaluationSubStage.AWAITING_BOARD).allowed());
    }
}
