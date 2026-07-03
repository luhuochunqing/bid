package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-474: 任务通知 targetUrl 解析策略单元测试。
 *
 * <p>验证根据被分配人角色码解析任务分配通知的 targetUrl：
 * <ul>
 *   <li>跨部门协同人员（bid-otherDept）→ /task-board?taskId=X&projectId=Y（任务看板）</li>
 *   <li>其他角色（含 null/空）→ /project/{projectId}/drafting（项目详情 drafting 阶段）</li>
 * </ul>
 *
 * <p>对应纯核心：{@link TaskNotificationTargetUrlResolver}。
 * 该类供 ProjectNotificationService 和 TaskReviewNotificationService 共用，
 * 避免 targetUrl 角色判定逻辑在多个 Service 中复制。
 */
class TaskNotificationTargetUrlResolverTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long TASK_ID = 200L;

    @Test
    @DisplayName("bid-otherDept 角色：应跳转到任务看板 /task-board?taskId=X&projectId=Y")
    void shouldReturnTaskBoardUrl_whenRoleIsBidOtherDept() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.BID_OTHER_DEPT_CODE);

        assertThat(url).isEqualTo("/task-board?taskId=" + TASK_ID + "&projectId=" + PROJECT_ID);
    }

    @Test
    @DisplayName("bid-Team 角色：应跳转到项目 drafting 页 /project/{projectId}/drafting")
    void shouldReturnProjectDraftingUrl_whenRoleIsBidTeam() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.BID_SPECIALIST_CODE);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("admin 角色：应跳转到项目 drafting 页 /project/{projectId}/drafting")
    void shouldReturnProjectDraftingUrl_whenRoleIsAdmin() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.ADMIN_CODE);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("null roleCode：应兜底跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleCodeIsNull() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, null);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("空字符串 roleCode：应兜底跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleCodeIsEmptyString() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, "");

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("空白 roleCode：应兜底跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleCodeIsBlank() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, "   ");

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("投标管理员 /bidAdmin：应跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleIsBidAdmin() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.BID_ADMIN_CODE);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("投标组长 bid-TeamLeader：应跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleIsBidTeamLeader() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.BID_LEAD_CODE);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("投标项目负责人 bid-projectLeader：应跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleIsProjectLeader() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.SALES_CODE);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }

    @Test
    @DisplayName("行政人员 bid-administration：应跳转到项目 drafting 页")
    void shouldReturnProjectDraftingUrl_whenRoleIsAdminStaff() {
        String url = TaskNotificationTargetUrlResolver.resolveTargetUrl(
                PROJECT_ID, TASK_ID, RoleProfileCatalog.ADMIN_STAFF_CODE);

        assertThat(url).isEqualTo("/project/" + PROJECT_ID + "/drafting");
    }
}
