package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskOperationPolicyTest {

    private static final String CURRENT_USER_ID = "user-123";
    private static final String OTHER_USER_ID = "user-456";
    private static final String PRIMARY_LEAD_ID = "lead-primary";
    private static final String SECONDARY_LEAD_ID = "lead-secondary";

    @Nested
    @DisplayName("canManageTask - 任务管理权限")
    class CanManageTaskTests {

        @Test
        @DisplayName("admin 角色直接放行")
        void adminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bidAdmin 角色直接放行")
        void bidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-TeamLeader 角色直接放行")
        void bidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 匹配 primaryLeadId 放行")
        void bidProjectLeader_MatchingPrimaryLead_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.SALES_CODE,
                    PRIMARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 不匹配 primaryLeadId 拒绝")
        void bidProjectLeader_NotMatchingPrimaryLead_ShouldBeDenied() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("bid-Team 匹配 primaryLeadId 放行")
        void bidTeam_MatchingPrimaryLead_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    PRIMARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-Team 匹配 secondaryLeadId 放行")
        void bidTeam_MatchingSecondaryLead_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    SECONDARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-Team 不匹配 primary/secondaryLeadId 拒绝")
        void bidTeam_NotMatchingAnyLead_ShouldBeDenied() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("bid-otherDept 角色拒绝")
        void bidOtherDeptRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("bid-administration 角色拒绝")
        void bidAdministrationRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.ADMIN_STAFF_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("null 角色拒绝")
        void nullRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canManageTask(
                    null,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("canSubmitTask - 任务提交权限")
    class CanSubmitTaskTests {

        @Test
        @DisplayName("执行人本人可以提交 - admin 角色")
        void assigneeIsCurrentUser_AdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bidAdmin 角色")
        void assigneeIsCurrentUser_BidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-TeamLeader 角色")
        void assigneeIsCurrentUser_BidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-projectLeader 角色")
        void assigneeIsCurrentUser_BidProjectLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.SALES_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-Team 角色")
        void assigneeIsCurrentUser_BidTeamRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-otherDept 角色")
        void assigneeIsCurrentUser_BidOtherDeptRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-administration 角色")
        void assigneeIsCurrentUser_BidAdministrationRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.ADMIN_STAFF_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("非执行人不能提交 - 即使是 admin 角色")
        void assigneeIsNotCurrentUser_AdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bidAdmin 角色")
        void assigneeIsNotCurrentUser_BidAdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-TeamLeader 角色")
        void assigneeIsNotCurrentUser_BidTeamLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-projectLeader 角色")
        void assigneeIsNotCurrentUser_BidProjectLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-Team 角色")
        void assigneeIsNotCurrentUser_BidTeamRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-otherDept 角色")
        void assigneeIsNotCurrentUser_BidOtherDeptRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-administration 角色")
        void assigneeIsNotCurrentUser_BidAdministrationRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.ADMIN_STAFF_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("null assigneeId 拒绝")
        void nullAssigneeId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    null,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("null currentUserId 拒绝")
        void nullCurrentUserId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canSubmitTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    null
            );
            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("canUploadDeliverable - 交付物上传权限")
    class CanUploadDeliverableTests {

        @Test
        @DisplayName("执行人本人可以上传 - admin 角色")
        void assigneeIsCurrentUser_AdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bidAdmin 角色")
        void assigneeIsCurrentUser_BidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-TeamLeader 角色")
        void assigneeIsCurrentUser_BidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-projectLeader 角色")
        void assigneeIsCurrentUser_BidProjectLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.SALES_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-Team 角色")
        void assigneeIsCurrentUser_BidTeamRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-otherDept 角色")
        void assigneeIsCurrentUser_BidOtherDeptRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-administration 角色")
        void assigneeIsCurrentUser_BidAdministrationRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.ADMIN_STAFF_CODE,
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("非执行人不能上传 - 即使是 admin 角色")
        void assigneeIsNotCurrentUser_AdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.ADMIN_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bidAdmin 角色")
        void assigneeIsNotCurrentUser_BidAdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-TeamLeader 角色")
        void assigneeIsNotCurrentUser_BidTeamLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-projectLeader 角色")
        void assigneeIsNotCurrentUser_BidProjectLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-Team 角色")
        void assigneeIsNotCurrentUser_BidTeamRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-otherDept 角色")
        void assigneeIsNotCurrentUser_BidOtherDeptRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-administration 角色")
        void assigneeIsNotCurrentUser_BidAdministrationRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.ADMIN_STAFF_CODE,
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("null assigneeId 拒绝")
        void nullAssigneeId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.ADMIN_CODE,
                    null,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("null currentUserId 拒绝")
        void nullCurrentUserId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canUploadDeliverable(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    null
            );
            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("canReviewTask - 任务审核权限")
    class CanReviewTaskTests {

        @Test
        @DisplayName("admin 角色直接放行")
        void adminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bidAdmin 角色直接放行")
        void bidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-TeamLeader 角色直接放行")
        void bidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 匹配 primaryLeadId 放行")
        void bidProjectLeader_MatchingPrimaryLead_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.SALES_CODE,
                    PRIMARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 不匹配 primaryLeadId 拒绝")
        void bidProjectLeader_NotMatchingPrimaryLead_ShouldBeDenied() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("bid-Team 匹配 primaryLeadId 放行")
        void bidTeam_MatchingPrimaryLead_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    PRIMARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-Team 匹配 secondaryLeadId 放行")
        void bidTeam_MatchingSecondaryLead_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    SECONDARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-Team 不匹配 primary/secondaryLeadId 拒绝")
        void bidTeam_NotMatchingAnyLead_ShouldBeDenied() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("bid-otherDept 角色拒绝")
        void bidOtherDeptRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("bid-administration 角色拒绝")
        void bidAdministrationRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.ADMIN_STAFF_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("null 角色拒绝")
        void nullRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canReviewTask(
                    null,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
        }
    }
}
