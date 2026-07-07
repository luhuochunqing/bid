// Input: ProjectTransferService 行为
// Output: Mockito 单元测试覆盖转移成功/失败/边界场景
// Pos: backend test source
// 维护声明: 覆盖 FR-001~FR-010 全部场景；通知/审计/角色校验独立测试。

package com.xiyu.bid.project.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.dto.ProjectTransferResponse;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTransferServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock TenderRepository tenderRepository;
    @Mock ProjectInitiationDetailsRepository initiationDetailsRepository;
    @Mock TenderAssignmentRecordRepository assignmentRecordRepository;
    @Mock ProjectTransferNotifier notifier;

    ProjectTransferService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTransferService(
                projectRepository, userRepository, tenderRepository,
                initiationDetailsRepository, assignmentRecordRepository,
                notifier);
    }

    private static RoleProfile roleProfile(String code) {
        return RoleProfile.builder().code(code).name(code).build();
    }

    // ── US1: 转移成功 ─────────────────────────────────────────────────────

    @Test
    void transfer_success_updates_project_initiationDetails_tender_and_audit() {
        // Given
        Project project = Project.builder()
                .id(135L).name("测试项目").managerId(7246L).tenderId(743L).build();
        User oldOwner = User.builder().id(7246L).fullName("陈梦瑶").build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(true)
                .departmentName("研发中心")
                .roleProfile(roleProfile("bid-projectLeader")).build();
        Tender tender = new Tender();
        tender.setId(743L);
        tender.setProjectManagerId(7246L);
        tender.setProjectManagerName("陈梦瑶");
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .id(1L).projectId(135L).ownerUserId(7246L).projectLeaderName("陈梦瑶").build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));
        when(userRepository.findById(7246L)).thenReturn(Optional.of(oldOwner));
        when(userRepository.findById(999L)).thenReturn(Optional.of(User.builder().id(999L).fullName("管理员").build()));
        when(tenderRepository.findById(743L)).thenReturn(Optional.of(tender));
        when(initiationDetailsRepository.findByProjectId(135L)).thenReturn(Optional.of(details));

        // When
        ProjectTransferResponse response = service.transfer(135L, 7324L, 999L, "误派修正");

        // Then - project 更新
        assertThat(project.getManagerId()).isEqualTo(7324L);
        verify(projectRepository).save(project);

        // Then - initiationDetails 更新
        assertThat(details.getOwnerUserId()).isEqualTo(7324L);
        assertThat(details.getProjectLeaderName()).isEqualTo("周子靖");
        // CO-537: 项目负责人部门应同步回填
        assertThat(details.getLeaderDepartment()).isEqualTo("研发中心");
        verify(initiationDetailsRepository).save(details);

        // Then - tender 更新
        assertThat(tender.getProjectManagerId()).isEqualTo(7324L);
        assertThat(tender.getProjectManagerName()).isEqualTo("周子靖");
        // CO-537: 标讯"项目部门"应同步回填
        assertThat(tender.getDepartment()).isEqualTo("研发中心");
        verify(tenderRepository).save(tender);

        // Then - 审计记录
        ArgumentCaptor<TenderAssignmentRecord> recordCaptor = ArgumentCaptor.forClass(TenderAssignmentRecord.class);
        verify(assignmentRecordRepository).save(recordCaptor.capture());
        TenderAssignmentRecord record = recordCaptor.getValue();
        assertThat(record.getTenderId()).isEqualTo(743L);
        assertThat(record.getAssigneeId()).isEqualTo(7324L);
        assertThat(record.getAssigneeName()).isEqualTo("周子靖");
        assertThat(record.getAssignedById()).isEqualTo(999L);
        assertThat(record.getType()).isEqualTo(TenderAssignmentRecord.AssignmentType.TRANSFER);
        assertThat(record.getRemark()).contains("项目转移").contains("陈梦瑶").contains("周子靖");

        // Then - 通知调用
        verify(notifier).notifyTransferred(135L, "测试项目", 7324L, "周子靖", "陈梦瑶", 999L, "管理员");

        // Then - 响应
        assertThat(response.getProjectId()).isEqualTo(135L);
        assertThat(response.getOldOwnerUserId()).isEqualTo(7246L);
        assertThat(response.getOldOwnerName()).isEqualTo("陈梦瑶");
        assertThat(response.getNewOwnerUserId()).isEqualTo(7324L);
        assertThat(response.getNewOwnerName()).isEqualTo("周子靖");
        assertThat(response.getTenderUpdated()).isTrue();
        assertThat(response.getTenderId()).isEqualTo(743L);
    }

    @Test
    void transfer_success_without_initiationDetails_skips_update() {
        // Given
        Project project = Project.builder()
                .id(135L).name("测试项目").managerId(7246L).tenderId(743L).build();
        User oldOwner = User.builder().id(7246L).fullName("陈梦瑶").build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(true)
                .roleProfile(roleProfile("admin")).build();
        Tender tender = new Tender();
        tender.setId(743L);

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));
        when(userRepository.findById(7246L)).thenReturn(Optional.of(oldOwner));
        when(userRepository.findById(999L)).thenReturn(Optional.of(User.builder().id(999L).fullName("管理员").build()));
        when(tenderRepository.findById(743L)).thenReturn(Optional.of(tender));
        when(initiationDetailsRepository.findByProjectId(135L)).thenReturn(Optional.empty());

        // When
        service.transfer(135L, 7324L, 999L, null);

        // Then - initiationDetails 未更新
        verify(initiationDetailsRepository, never()).save(any());
    }

    @Test
    void transfer_success_without_tender_skips_tender_update() {
        // Given: project.tenderId = null
        Project project = Project.builder()
                .id(135L).name("测试项目").managerId(7246L).tenderId(null).build();
        User oldOwner = User.builder().id(7246L).fullName("陈梦瑶").build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(true)
                .roleProfile(roleProfile("admin")).build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));
        when(userRepository.findById(7246L)).thenReturn(Optional.of(oldOwner));
        when(userRepository.findById(999L)).thenReturn(Optional.of(User.builder().id(999L).fullName("管理员").build()));
        when(initiationDetailsRepository.findByProjectId(135L)).thenReturn(Optional.empty());

        // When
        ProjectTransferResponse response = service.transfer(135L, 7324L, 999L, null);

        // Then
        verify(tenderRepository, never()).findById(any());
        verify(tenderRepository, never()).save(any());
        verify(assignmentRecordRepository, never()).save(any());
        assertThat(response.getTenderUpdated()).isFalse();
        assertThat(response.getTenderId()).isNull();
    }

    // ── US2: 校验失败 ─────────────────────────────────────────────────────

    @Test
    void transfer_projectNotFound_throws_404() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transfer(999L, 7324L, 999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transfer_newOwnerNotFound_throws_404() {
        Project project = Project.builder().id(135L).managerId(7246L).tenderId(743L).build();
        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(135L, 7324L, 999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transfer_newOwnerEqualsOldManager_throws_IllegalArgumentException() {
        Project project = Project.builder().id(135L).managerId(7324L).tenderId(743L).build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(true)
                .roleProfile(roleProfile("bid-projectLeader")).build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));

        assertThatThrownBy(() -> service.transfer(135L, 7324L, 999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相同");
    }

    @Test
    void transfer_newOwnerEqualsOldOwnerUserId_throws_IllegalArgumentException() {
        // managerId 不同，但 initiationDetails.owner_user_id 与新负责人相同
        Project project = Project.builder().id(135L).managerId(7246L).tenderId(743L).build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(true)
                .roleProfile(roleProfile("bid-projectLeader")).build();
        ProjectInitiationDetails details = ProjectInitiationDetails.builder()
                .id(1L).projectId(135L).ownerUserId(7324L).projectLeaderName("周子靖").build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));
        when(initiationDetailsRepository.findByProjectId(135L)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> service.transfer(135L, 7324L, 999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相同");
    }

    @Test
    void transfer_newOwnerDisabled_throws_IllegalArgumentException() {
        Project project = Project.builder().id(135L).managerId(7246L).tenderId(743L).build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(false)
                .roleProfile(roleProfile("bid-projectLeader")).build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));

        assertThatThrownBy(() -> service.transfer(135L, 7324L, 999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("停用");
    }

    @Test
    void transfer_newOwnerRoleInvalid_throws_IllegalArgumentException() {
        Project project = Project.builder().id(135L).managerId(7246L).tenderId(743L).build();
        User newOwner = User.builder().id(7324L).fullName("陈梦瑶").enabled(true)
                .roleProfile(roleProfile("bid-Team")).build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));

        assertThatThrownBy(() -> service.transfer(135L, 7324L, 999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是投标项目负责人");
    }

    @Test
    void transfer_newOwnerRoleNull_throws_IllegalArgumentException() {
        Project project = Project.builder().id(135L).managerId(7246L).tenderId(743L).build();
        User newOwner = User.builder().id(7324L).fullName("周子靖").enabled(true).build();

        when(projectRepository.findById(135L)).thenReturn(Optional.of(project));
        when(userRepository.findById(7324L)).thenReturn(Optional.of(newOwner));

        assertThatThrownBy(() -> service.transfer(135L, 7324L, 999L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("新负责人必须是投标项目负责人/组长/管理员");
    }
}
