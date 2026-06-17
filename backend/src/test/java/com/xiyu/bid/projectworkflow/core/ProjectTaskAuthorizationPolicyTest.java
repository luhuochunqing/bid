// Input: 角色 code + 任务状态 + 是否为任务指派人
// Output: 验证 ProjectTaskAuthorizationPolicy 四类方法在正常/异常路径下的 Decision
// Pos: Test/核心策略测试
package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目任务授权策略单元测试（蓝图 §2.3.1）。
 * <p>覆盖 {@link ProjectTaskAuthorizationPolicy} 所有方法的 permit + IDENTITY deny 路径。</p>
 */
class ProjectTaskAuthorizationPolicyTest {

    // ── canManageTask ───────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"admin", "bid_admin", "bid_lead", "bid_senior", "bid_specialist"})
    void canManageTask_whenAllowedRole_shouldPermit(String roleCode) {
        var result = ProjectTaskAuthorizationPolicy.canManageTask(roleCode);
        assertThat(result.allowed()).isTrue();
        assertThat(result.cause()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"task_executor", "bid_other_dept", "sales", "admin_staff"})
    void canManageTask_whenDisallowedRole_shouldDenyIdentity(String roleCode) {
        var result = ProjectTaskAuthorizationPolicy.canManageTask(roleCode);
        assertThat(result.allowed()).isFalse();
        assertThat(result.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(result.reason()).contains("无权管理");
    }

    @Test
    void canManageTask_whenNullRole_shouldDenyIdentity() {
        var result = ProjectTaskAuthorizationPolicy.canManageTask(null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
    }

    // ── canSubmitTask ───────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"task_executor", "bid_other_dept", "sales", "admin", "bid_lead"})
    void canSubmitTask_whenIsAssignee_shouldPermitRegardlessOfRole(String roleCode) {
        var result = ProjectTaskAuthorizationPolicy.canSubmitTask(roleCode, true);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void canSubmitTask_whenNotAssignee_shouldDenyIdentity() {
        var result = ProjectTaskAuthorizationPolicy.canSubmitTask("task_executor", false);
        assertThat(result.allowed()).isFalse();
        assertThat(result.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(result.reason()).contains("仅任务执行人本人");
    }

    @Test
    void canSubmitTask_whenNotAssigneeAndAdminRole_shouldStillDeny() {
        // 角色无关：admin 但非指派人也不可提交
        var result = ProjectTaskAuthorizationPolicy.canSubmitTask("admin", false);
        assertThat(result.allowed()).isFalse();
        assertThat(result.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
    }

    // ── canReviewTask ───────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"admin", "bid_admin", "bid_lead", "bid_senior", "bid_specialist"})
    void canReviewTask_whenAllowedRole_shouldPermit(String roleCode) {
        var result = ProjectTaskAuthorizationPolicy.canReviewTask(roleCode);
        assertThat(result.allowed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"task_executor", "bid_other_dept", "sales", "admin_staff"})
    void canReviewTask_whenDisallowedRole_shouldDenyIdentity(String roleCode) {
        var result = ProjectTaskAuthorizationPolicy.canReviewTask(roleCode);
        assertThat(result.allowed()).isFalse();
        assertThat(result.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(result.reason()).contains("无权审核");
    }

    // ── decideStatusTransition ──────────────────────────────────────────

    @Test
    void decideStatusTransition_fromReviewToCompleted_shouldRouteToCanReviewTask() {
        // REVIEW→COMPLETED：管理员可审核
        var allowed = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "REVIEW", "COMPLETED", "bid_admin", false);
        assertThat(allowed.allowed()).isTrue();
        // 执行人不可审核
        var denied = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "REVIEW", "COMPLETED", "task_executor", true);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(denied.reason()).contains("无权审核");
    }

    @Test
    void decideStatusTransition_fromReviewToTodo_shouldRouteToCanReviewTask() {
        // REVIEW→TODO(驳回)：组长可审核驳回
        var allowed = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "REVIEW", "TODO", "bid_lead", false);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void decideStatusTransition_fromTodoToReview_shouldRouteToCanSubmitTask() {
        // TODO→REVIEW(提交)：指派人本人可提交，角色无关
        var allowed = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "TODO", "REVIEW", "task_executor", true);
        assertThat(allowed.allowed()).isTrue();
        // 非指派人不可提交
        var denied = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "TODO", "REVIEW", "task_executor", false);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
    }

    @Test
    void decideStatusTransition_fromInProgressToReview_shouldRouteToCanSubmitTask() {
        var allowed = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "IN_PROGRESS", "REVIEW", "bid_other_dept", true);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void decideStatusTransition_fromTodoToInProgress_shouldRouteToCanSubmitTask() {
        // TODO→IN_PROGRESS：执行人开始执行
        var allowed = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "TODO", "IN_PROGRESS", "sales", true);
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void decideStatusTransition_otherTransitions_shouldRouteToCanManageTask() {
        // 其余转换（如直接置 COMPLETED/CANCELLED）走管理权限
        var allowed = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "TODO", "COMPLETED", "bid_admin", false);
        assertThat(allowed.allowed()).isTrue();
        // 非管理角色不可
        var denied = ProjectTaskAuthorizationPolicy.decideStatusTransition(
                "TODO", "COMPLETED", "task_executor", true);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.cause()).isEqualTo(ProjectTaskAuthorizationPolicy.Decision.Cause.IDENTITY);
        assertThat(denied.reason()).contains("无权管理");
    }
}
