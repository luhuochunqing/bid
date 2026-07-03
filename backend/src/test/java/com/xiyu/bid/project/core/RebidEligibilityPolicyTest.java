// Input: 项目阶段 (ProjectStage)
// Output: JUnit5 assertions covering all 6 stages + null input
// Pos: backend test source - pure JUnit5, no Spring
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebidEligibilityPolicyTest {

    @Test
    void closed_allowsRebid() {
        var d = RebidEligibilityPolicy.decide(ProjectStage.CLOSED);
        assertTrue(d.allowed());
        assertInstanceOf(RebidEligibilityPolicy.Decision.Allow.class, d);
    }

    @Test
    void retrospective_denied() {
        var d = RebidEligibilityPolicy.decide(ProjectStage.RETROSPECTIVE);
        assertFalse(d.allowed());
        assertInstanceOf(RebidEligibilityPolicy.Decision.Deny.class, d);
        assertEquals("项目尚未结项，无法二次招标",
                ((RebidEligibilityPolicy.Decision.Deny) d).reason());
    }

    @Test
    void resultPending_denied() {
        var d = RebidEligibilityPolicy.decide(ProjectStage.RESULT_PENDING);
        assertFalse(d.allowed());
        assertInstanceOf(RebidEligibilityPolicy.Decision.Deny.class, d);
    }

    @Test
    void evaluating_denied() {
        var d = RebidEligibilityPolicy.decide(ProjectStage.EVALUATING);
        assertFalse(d.allowed());
        assertInstanceOf(RebidEligibilityPolicy.Decision.Deny.class, d);
    }

    @Test
    void drafting_denied() {
        var d = RebidEligibilityPolicy.decide(ProjectStage.DRAFTING);
        assertFalse(d.allowed());
        assertInstanceOf(RebidEligibilityPolicy.Decision.Deny.class, d);
    }

    @Test
    void initiated_denied() {
        var d = RebidEligibilityPolicy.decide(ProjectStage.INITIATED);
        assertFalse(d.allowed());
        assertInstanceOf(RebidEligibilityPolicy.Decision.Deny.class, d);
    }

    @Test
    void null_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> RebidEligibilityPolicy.decide(null));
    }
}
