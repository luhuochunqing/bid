// Output: ProjectNotificationRecipientPolicy 全 ProjectNotificationRole 解析、去重、排除、降级覆盖
// Pos: notification/service/ - 项目通知接收人策略测试
package com.xiyu.bid.notification.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.xiyu.bid.notification.core.ProjectNotificationRole.BID_ADMIN;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.BID_ASSISTANT;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.BID_LEAD;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.BID_REVIEWER;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.BID_TEAM_LEADER;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.PROJECT_MEMBER;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.PROJECT_OWNER;
import static com.xiyu.bid.notification.core.ProjectNotificationRole.TASK_EXECUTOR;
import static com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicyFixtures.assignment;
import static com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicyFixtures.initiationDetails;
import static com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicyFixtures.member;
import static com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicyFixtures.review;
import static com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicyFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectNotificationRecipientPolicy — 项目通知接收人策略")
class ProjectNotificationRecipientPolicyTest {

    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    @Mock private ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    @Mock private BidDocumentReviewRepository bidDocumentReviewRepository;

    private ProjectNotificationRecipientPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProjectNotificationRecipientPolicy(
                userRepository, projectMemberRepository, projectLeadAssignmentRepository,
                projectInitiationDetailsRepository, bidDocumentReviewRepository);
    }

    @Test
    @DisplayName("BID_ADMIN：按角色码 /bidAdmin 解析启用用户")
    void bidAdmin_resolvesEnabledUsersByRoleCode() {
        when(userRepository.findEnabledByRoleProfileCodes(List.of(RoleProfileCatalog.BID_ADMIN_CODE)))
                .thenReturn(List.of(user(1L), user(2L)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_ADMIN), null))
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("BID_TEAM_LEADER：按角色码 bid-TeamLeader 解析启用用户")
    void bidTeamLeader_resolvesEnabledUsersByRoleCode() {
        when(userRepository.findEnabledByRoleProfileCodes(List.of(RoleProfileCatalog.BID_LEAD_CODE)))
                .thenReturn(List.of(user(3L)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_TEAM_LEADER), null))
                .containsExactly(3L);
    }

    @Test
    @DisplayName("BID_LEAD：取 ProjectLeadAssignment.primaryLeadUserId")
    void bidLead_resolvesPrimaryLeadUserId() {
        when(projectLeadAssignmentRepository.findByProjectId(100L))
                .thenReturn(Optional.of(assignment(4L, null)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_LEAD), null))
                .containsExactly(4L);
    }

    @Test
    @DisplayName("BID_ASSISTANT：仅取 ProjectLeadAssignment.secondaryLeadUserId（不广播 bid-Team 全局角色）")
    void bidAssistant_resolvesSecondaryLeadOnly() {
        when(projectLeadAssignmentRepository.findByProjectId(100L))
                .thenReturn(Optional.of(assignment(null, 6L)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_ASSISTANT), null))
                .containsExactly(6L);
    }

    @Test
    @DisplayName("PROJECT_OWNER：取 ProjectInitiationDetails.ownerUserId")
    void projectOwner_resolvesOwnerUserId() {
        when(projectInitiationDetailsRepository.findByProjectId(100L))
                .thenReturn(Optional.of(initiationDetails(7L)));

        assertThat(policy.resolveRecipients(100L, Set.of(PROJECT_OWNER), null))
                .containsExactly(7L);
    }

    @Test
    @DisplayName("TASK_EXECUTOR：将调用方传入的 assigneeId 加入结果；未传入时为空")
    void taskExecutor_includesAssigneeIdOrEmpty() {
        assertThat(policy.resolveRecipients(100L, Set.of(TASK_EXECUTOR), null, 8L))
                .containsExactly(8L);
        assertThat(policy.resolveRecipients(100L, Set.of(TASK_EXECUTOR), null))
                .isEmpty();
    }

    @Test
    @DisplayName("BID_REVIEWER：取 BidDocumentReviewEntity.reviewerId")
    void bidReviewer_resolvesReviewerId() {
        when(bidDocumentReviewRepository.findByProjectId(100L))
                .thenReturn(Optional.of(review(9L)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_REVIEWER), null))
                .containsExactly(9L);
    }

    @Test
    @DisplayName("PROJECT_MEMBER：取 sys_project_member 全员 userId")
    void projectMember_resolvesAllProjectMembers() {
        when(projectMemberRepository.findByProjectId(100L))
                .thenReturn(List.of(member(10L), member(11L)));

        assertThat(policy.resolveRecipients(100L, Set.of(PROJECT_MEMBER), null))
                .containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("多角色混合：按枚举声明顺序稳定合并并去重")
    void multipleRoles_mergesInStableOrderAndDeduplicates() {
        when(userRepository.findEnabledByRoleProfileCodes(List.of(RoleProfileCatalog.BID_ADMIN_CODE)))
                .thenReturn(List.of(user(1L)));
        when(projectLeadAssignmentRepository.findByProjectId(100L))
                .thenReturn(Optional.of(assignment(1L, 2L)));
        when(projectInitiationDetailsRepository.findByProjectId(100L))
                .thenReturn(Optional.of(initiationDetails(2L)));
        when(bidDocumentReviewRepository.findByProjectId(100L))
                .thenReturn(Optional.of(review(3L)));
        when(projectMemberRepository.findByProjectId(100L))
                .thenReturn(List.of(member(3L), member(4L)));

        List<Long> result = policy.resolveRecipients(
                100L,
                Set.of(PROJECT_MEMBER, BID_ADMIN, BID_LEAD, PROJECT_OWNER, BID_REVIEWER),
                null);

        assertThat(result).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("excludeUserId：从结果中排除指定用户")
    void excludeUserId_removesSpecifiedUser() {
        when(userRepository.findEnabledByRoleProfileCodes(List.of(RoleProfileCatalog.BID_ADMIN_CODE)))
                .thenReturn(List.of(user(1L), user(2L), user(3L)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_ADMIN), 2L))
                .containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("空输入：projectId 或 roles 为空时返回空列表")
    void emptyInput_returnsEmptyList() {
        assertThat(policy.resolveRecipients(null, Set.of(BID_ADMIN), null)).isEmpty();
        assertThat(policy.resolveRecipients(100L, null, null)).isEmpty();
        assertThat(policy.resolveRecipients(100L, Set.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("实体不存在或字段为 null：缺失 assignment/details/review 时返回空列表")
    void missingEntitiesOrNullFields_returnsEmpty() {
        when(projectLeadAssignmentRepository.findByProjectId(100L))
                .thenReturn(Optional.of(assignment(null, null)));
        when(projectInitiationDetailsRepository.findByProjectId(100L))
                .thenReturn(Optional.of(initiationDetails(null)));
        when(bidDocumentReviewRepository.findByProjectId(100L))
                .thenReturn(Optional.of(review(null)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_LEAD, PROJECT_OWNER, BID_REVIEWER), null))
                .isEmpty();
    }

    @Test
    @DisplayName("DB 异常降级：单个 Repository 异常时保留已收集结果并继续后续查询")
    void dbException_degradesAndContinues() {
        when(userRepository.findEnabledByRoleProfileCodes(List.of(RoleProfileCatalog.BID_ADMIN_CODE)))
                .thenThrow(new RuntimeException("user db down"));
        when(projectLeadAssignmentRepository.findByProjectId(100L))
                .thenReturn(Optional.of(assignment(4L, 5L)));
        when(projectInitiationDetailsRepository.findByProjectId(100L))
                .thenThrow(new RuntimeException("details db down"));
        when(bidDocumentReviewRepository.findByProjectId(100L))
                .thenReturn(Optional.of(review(6L)));

        assertThat(policy.resolveRecipients(100L, Set.of(BID_ADMIN, BID_LEAD, PROJECT_OWNER, BID_REVIEWER), null))
                .containsExactly(4L, 6L);
    }

    @Test
    @DisplayName("DB 异常降级：所有 Repository 均异常时返回空列表")
    void dbException_allDown_returnsEmpty() {
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenThrow(new RuntimeException("user db down"));
        when(projectLeadAssignmentRepository.findByProjectId(any()))
                .thenThrow(new RuntimeException("assignment db down"));
        when(projectInitiationDetailsRepository.findByProjectId(any()))
                .thenThrow(new RuntimeException("details db down"));
        when(bidDocumentReviewRepository.findByProjectId(any()))
                .thenThrow(new RuntimeException("review db down"));
        when(projectMemberRepository.findByProjectId(any()))
                .thenThrow(new RuntimeException("member db down"));

        assertThat(policy.resolveRecipients(
                100L,
                Set.of(BID_ADMIN, BID_LEAD, PROJECT_OWNER, BID_REVIEWER, PROJECT_MEMBER),
                null)).isEmpty();
    }
}
