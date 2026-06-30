package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskOperationPolicyTest {

    private static final Long CURRENT_USER_ID = 123L;
    private static final Long OTHER_USER_ID = 456L;
    private static final Long PRIMARY_LEAD_ID = 1L;
    private static final Long SECONDARY_LEAD_ID = 2L;

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
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bidAdmin 角色")
        void assigneeIsCurrentUser_BidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-TeamLeader 角色")
        void assigneeIsCurrentUser_BidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-projectLeader 角色")
        void assigneeIsCurrentUser_BidProjectLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-Team 角色")
        void assigneeIsCurrentUser_BidTeamRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-otherDept 角色")
        void assigneeIsCurrentUser_BidOtherDeptRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以提交 - bid-administration 角色")
        void assigneeIsCurrentUser_BidAdministrationRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("非执行人不能提交 - 即使是 admin 角色")
        void assigneeIsNotCurrentUser_AdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bidAdmin 角色")
        void assigneeIsNotCurrentUser_BidAdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-TeamLeader 角色")
        void assigneeIsNotCurrentUser_BidTeamLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-projectLeader 角色")
        void assigneeIsNotCurrentUser_BidProjectLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-Team 角色")
        void assigneeIsNotCurrentUser_BidTeamRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-otherDept 角色")
        void assigneeIsNotCurrentUser_BidOtherDeptRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能提交 - bid-administration 角色")
        void assigneeIsNotCurrentUser_BidAdministrationRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("null assigneeId 拒绝")
        void nullAssigneeId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    null,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("null currentUserId 拒绝")
        void nullCurrentUserId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
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
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bidAdmin 角色")
        void assigneeIsCurrentUser_BidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-TeamLeader 角色")
        void assigneeIsCurrentUser_BidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-projectLeader 角色")
        void assigneeIsCurrentUser_BidProjectLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-Team 角色")
        void assigneeIsCurrentUser_BidTeamRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-otherDept 角色")
        void assigneeIsCurrentUser_BidOtherDeptRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("执行人本人可以上传 - bid-administration 角色")
        void assigneeIsCurrentUser_BidAdministrationRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("非执行人不能上传 - 即使是 admin 角色")
        void assigneeIsNotCurrentUser_AdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bidAdmin 角色")
        void assigneeIsNotCurrentUser_BidAdminRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-TeamLeader 角色")
        void assigneeIsNotCurrentUser_BidTeamLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-projectLeader 角色")
        void assigneeIsNotCurrentUser_BidProjectLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-Team 角色")
        void assigneeIsNotCurrentUser_BidTeamRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-otherDept 角色")
        void assigneeIsNotCurrentUser_BidOtherDeptRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("非执行人不能上传 - bid-administration 角色")
        void assigneeIsNotCurrentUser_BidAdministrationRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    OTHER_USER_ID,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isNotBlank();
        }

        @Test
        @DisplayName("null assigneeId 拒绝")
        void nullAssigneeId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    null,
                    CURRENT_USER_ID
            );
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("null currentUserId 拒绝")
        void nullCurrentUserId_ShouldBeDenied() {
            var result = TaskOperationPolicy.canActAsAssignee(
                    CURRENT_USER_ID,
                    null
            );
            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("canForceReassign - 强行干预（重分配）权限")
    class CanForceReassignTests {

        @Test
        @DisplayName("admin 角色可强行重分配")
        void adminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canForceReassign(
                    RoleProfileCatalog.ADMIN_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bidAdmin 角色可强行重分配")
        void bidAdminRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canForceReassign(
                    RoleProfileCatalog.BID_ADMIN_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-TeamLeader 角色可强行重分配")
        void bidTeamLeaderRole_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canForceReassign(
                    RoleProfileCatalog.BID_LEAD_CODE,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 不可强行重分配")
        void bidProjectLeaderRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canForceReassign(
                    RoleProfileCatalog.SALES_CODE,
                    PRIMARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("管理员/组长");
        }

        @Test
        @DisplayName("bid-Team 不可强行重分配")
        void bidTeamRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canForceReassign(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    PRIMARY_LEAD_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("管理员/组长");
        }

        @Test
        @DisplayName("null 角色拒绝")
        void nullRole_ShouldBeDenied() {
            var result = TaskOperationPolicy.canForceReassign(
                    null,
                    CURRENT_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
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
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID
            );
            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("CO-361: canManageTask - 项目立项负责人（isProjectOwner）")
    class CanManageTaskWithProjectOwnerTests {

        @Test
        @DisplayName("bid-projectLeader 作为 isProjectOwner=true 放行（即使不匹配 primaryLeadId）")
        void bidProjectLeader_AsProjectOwner_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    true
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 作为 isProjectOwner=false 且不匹配 primaryLeadId 拒绝")
        void bidProjectLeader_NotOwnerNotLead_ShouldBeDenied() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    false
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("投标项目负责人");
        }

        @Test
        @DisplayName("isProjectOwner=true 不影响 admin 直接放行")
        void admin_AsProjectOwner_ShouldBeAllowed() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.ADMIN_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    true
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("isProjectOwner=true 不影响 bid-Team（仍需匹配 lead）")
        void bidTeam_AsProjectOwner_ShouldStillRequireLeadMatch() {
            var result = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.BID_SPECIALIST_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    true
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("投标专员");
        }

        @Test
        @DisplayName("向后兼容：4 参数重载等价于 isProjectOwner=false")
        void backwardCompat_FourArgEquivalentToFalse() {
            var four = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID
            );
            var five = TaskOperationPolicy.canManageTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    false
            );
            assertThat(five.allowed()).isEqualTo(four.allowed());
            assertThat(five.reason()).isEqualTo(four.reason());
        }
    }

    @Nested
    @DisplayName("CO-361: canReviewTask - 项目立项负责人（isProjectOwner）")
    class CanReviewTaskWithProjectOwnerTests {

        @Test
        @DisplayName("bid-projectLeader 作为 isProjectOwner=true 可审核（非自审）")
        void bidProjectLeader_AsProjectOwner_CanReview() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    999L,
                    true
            );
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("bid-projectLeader 作为 isProjectOwner=true 仍不能审核自己提交的任务（职责分离）")
        void bidProjectLeader_AsProjectOwner_CannotSelfReview() {
            var result = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    OTHER_USER_ID,
                    true
            );
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("不能审核自己提交的任务");
        }

        @Test
        @DisplayName("向后兼容：5 参数重载等价于 isProjectOwner=false")
        void backwardCompat_FiveargEquivalentToFalse() {
            var five = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    999L
            );
            var six = TaskOperationPolicy.canReviewTask(
                    RoleProfileCatalog.SALES_CODE,
                    OTHER_USER_ID,
                    PRIMARY_LEAD_ID,
                    SECONDARY_LEAD_ID,
                    999L,
                    false
            );
            assertThat(six.allowed()).isEqualTo(five.allowed());
            assertThat(six.reason()).isEqualTo(five.reason());
        }
    }
}
