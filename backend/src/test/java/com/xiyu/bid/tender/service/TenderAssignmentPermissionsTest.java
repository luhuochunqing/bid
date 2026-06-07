// Input: 模拟 TenderAssignmentRecordRepository 行为 + 输入 (tenderId, userId)
// Output: 验证 TenderAssignmentPermissions 的 canFill / canDecide 实例级判定
// Pos: backend test source - 单元级 (Mockito)
// Phase: TDD RED — 当前 MUST 失败，因为 TenderAssignmentPermissions 尚未实现
package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 实例级权限工具的契约测试。
 *
 * <p>纯单元，只对 helper + repo 交互断言；不接触数据库或 spring context。
 */
@ExtendWith(MockitoExtension.class)
class TenderAssignmentPermissionsTest {

    private static final Long TENDER_ID = 100L;
    private static final Long ASSIGNEE_ID = 7L;
    private static final Long ASSIGNED_BY_ID = 1L;

    @Mock
    private TenderAssignmentRecordRepository repository;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private UserRepository userRepository;

    private TenderAssignmentPermissions permissions;

    @BeforeEach
    void setUp() {
        permissions = new TenderAssignmentPermissions(repository, tenderRepository, userRepository);
    }

    // ---------- canFill ----------

    @Test
    @DisplayName("canFill: assigneeId 与 userId 相同且状态为 TRACKING → true")
    void canFill_matchingAssignee_tracking_returnsTrue() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.TRACKING)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.of(record(ASSIGNEE_ID, ASSIGNED_BY_ID)));

        assertThat(permissions.canFill(TENDER_ID, ASSIGNEE_ID)).isTrue();
    }

    @Test
    @DisplayName("canFill: assigneeId 与 userId 不同 → false")
    void canFill_differentUser_returnsFalse() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.PENDING_ASSIGNMENT)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.of(record(ASSIGNEE_ID, ASSIGNED_BY_ID)));

        assertThat(permissions.canFill(TENDER_ID, 999L)).isFalse();
    }

    @Test
    @DisplayName("canFill: status is TRACKING 且有 assignment 记录 → true")
    void canFill_trackingStatus_withAssignment_returnsTrue() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.TRACKING)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.of(record(ASSIGNEE_ID, ASSIGNED_BY_ID)));

        assertThat(permissions.canFill(TENDER_ID, ASSIGNEE_ID)).isTrue();
    }

    @Test
    @DisplayName("canFill: status is EVALUATED → false")
    void canFill_evaluatedStatus_returnsFalse() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.EVALUATED)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThat(permissions.canFill(TENDER_ID, ASSIGNEE_ID)).isFalse();
    }

    @Test
    @DisplayName("canFill: status is BIDDING → false")
    void canFill_biddingStatus_returnsFalse() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.BIDDING)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThat(permissions.canFill(TENDER_ID, ASSIGNEE_ID)).isFalse();
    }

    @Test
    @DisplayName("canFill: status is ABANDONED → false")
    void canFill_abandonedStatus_returnsFalse() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.ABANDONED)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));

        assertThat(permissions.canFill(TENDER_ID, ASSIGNEE_ID)).isFalse();
    }

    @Test
    @DisplayName("canFill: 无 latest assignment 记录 → false（标讯未分配）")
    void canFill_noAssignment_returnsFalse() {
        com.xiyu.bid.entity.Tender tender = com.xiyu.bid.entity.Tender.builder()
                .id(TENDER_ID)
                .status(com.xiyu.bid.entity.Tender.Status.PENDING_ASSIGNMENT)
                .build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.empty());

        assertThat(permissions.canFill(TENDER_ID, ASSIGNEE_ID)).isFalse();
    }

    @Test
    @DisplayName("canFill: userId 为 null → false（不查表也直接拒绝）")
    void canFill_nullUserId_returnsFalse() {
        assertThat(permissions.canFill(TENDER_ID, null)).isFalse();
    }

    // ---------- canDecide ----------

    @Test
    @DisplayName("canDecide: assignedById 与 userId 相同 → true")
    void canDecide_matchingAssigner_returnsTrue() {
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.of(record(ASSIGNEE_ID, ASSIGNED_BY_ID)));

        assertThat(permissions.canDecide(TENDER_ID, ASSIGNED_BY_ID)).isTrue();
    }

    @Test
    @DisplayName("canDecide: assignedById 与 userId 不同 → false")
    void canDecide_differentUser_returnsFalse() {
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.of(record(ASSIGNEE_ID, ASSIGNED_BY_ID)));

        assertThat(permissions.canDecide(TENDER_ID, 999L)).isFalse();
    }

    @Test
    @DisplayName("canDecide: assignedById 为 null（旧数据） → false")
    void canDecide_nullAssignedBy_returnsFalse() {
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.of(record(ASSIGNEE_ID, null)));

        assertThat(permissions.canDecide(TENDER_ID, ASSIGNED_BY_ID)).isFalse();
    }

    @Test
    @DisplayName("canDecide: 无 latest assignment 记录 → false")
    void canDecide_noAssignment_returnsFalse() {
        when(repository.findFirstByTenderIdOrderByAssignedAtDesc(TENDER_ID))
                .thenReturn(Optional.empty());

        assertThat(permissions.canDecide(TENDER_ID, ASSIGNED_BY_ID)).isFalse();
    }

    // ---------- tenderId null 防御 ----------

    @Test
    @DisplayName("canFill / canDecide: tenderId 为 null → false（不查表）")
    void nullTenderId_returnsFalse() {
        assertThat(permissions.canFill(null, ASSIGNEE_ID)).isFalse();
        assertThat(permissions.canDecide(null, ASSIGNED_BY_ID)).isFalse();
    }

    // ---------- helpers ----------

    private TenderAssignmentRecord record(Long assigneeId, Long assignedById) {
        return TenderAssignmentRecord.builder()
                .id(1L)
                .tenderId(TENDER_ID)
                .assigneeId(assigneeId)
                .assigneeName("assignee")
                .assignedById(assignedById)
                .assignedByName(assignedById == null ? null : "assigner")
                .build();
    }
}
