// Input: ProjectStage × fieldName 矩阵
// Output: JUnit5 断言覆盖 §3.6 全字段锁定 + §3.1.2 立项锁定字段
// Pos: backend test source - pure JUnit5
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFieldLockPolicyTest {

    // ----- CLOSED 全字段锁定（PRD §3.6 核心） -----

    @ParameterizedTest
    @EnumSource(value = ProjectStage.class, names = {"CLOSED"})
    void closed_anyField_denied(ProjectStage stage) {
        for (String f : new String[]{"summary", "depositAmount", "ownerUnit", "bidOpenTime", "evaluationNotes", "any"}) {
            var d = ProjectFieldLockPolicy.assertWritable(stage, f);
            assertFalse(d.allowed(), "expected denied for field=" + f);
            var deny = assertInstanceOf(ProjectFieldLockPolicy.Decision.Deny.class, d);
            assertTrue(deny.reason().contains("全字段锁定"));
        }
    }

    // ----- 立项锁定字段（§3.1.2）所有非 CLOSED 阶段都拒绝 -----

    @ParameterizedTest
    @EnumSource(value = ProjectStage.class, names = {"INITIATED", "DRAFTING", "EVALUATING", "RESULT_PENDING", "RETROSPECTIVE"})
    void initiationLockedField_bidOpenTime_denied(ProjectStage stage) {
        var d = ProjectFieldLockPolicy.assertWritable(stage, "bidOpenTime");
        assertFalse(d.allowed());
        var deny = assertInstanceOf(ProjectFieldLockPolicy.Decision.Deny.class, d);
        assertTrue(deny.reason().contains("提交后不可修改"));
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStage.class, names = {"INITIATED", "DRAFTING", "EVALUATING", "RESULT_PENDING", "RETROSPECTIVE"})
    void initiationLockedField_ownerUnit_denied(ProjectStage stage) {
        var d = ProjectFieldLockPolicy.assertWritable(stage, "ownerUnit");
        assertFalse(d.allowed());
    }

    // ----- 普通字段在非 CLOSED 阶段允许写 -----

    @ParameterizedTest
    @EnumSource(value = ProjectStage.class, names = {"INITIATED", "DRAFTING", "EVALUATING", "RESULT_PENDING", "RETROSPECTIVE"})
    void normalField_inAnyOpenStage_allowed(ProjectStage stage) {
        for (String f : new String[]{"summary", "depositAmount", "evaluationNotes", "competitors"}) {
            var d = ProjectFieldLockPolicy.assertWritable(stage, f);
            assertTrue(d.allowed(), "expected allow for field=" + f + " stage=" + stage);
        }
    }

    // ----- null/空字段名 防御 -----

    @Test
    void nullFieldName_denied() {
        var d = ProjectFieldLockPolicy.assertWritable(ProjectStage.DRAFTING, null);
        assertFalse(d.allowed());
    }

    @Test
    void blankFieldName_denied() {
        var d = ProjectFieldLockPolicy.assertWritable(ProjectStage.DRAFTING, "  ");
        assertFalse(d.allowed());
    }

    @Test
    void nullStage_throws() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> ProjectFieldLockPolicy.assertWritable(null, "summary"));
    }

    // ----- CLOSED 锁定优先于立项锁定 -----

    @Test
    void closed_evenInitiationLockedField_returnsClosedReason() {
        var d = ProjectFieldLockPolicy.assertWritable(ProjectStage.CLOSED, "ownerUnit");
        var deny = assertInstanceOf(ProjectFieldLockPolicy.Decision.Deny.class, d);
        assertTrue(deny.reason().contains("全字段锁定"));
    }
}
