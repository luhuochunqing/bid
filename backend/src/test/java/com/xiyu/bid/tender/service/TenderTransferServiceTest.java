package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenderTransferService 测试。
 *
 * <p>CO-261 根因二-b: 标讯转派成功后不给新负责人发通知。
 *
 * <p>状态收口契约（飞书《标讯中心·权限矩阵》第 9 条 + 第 2.1 分发转派）：
 * 锁定当前实现"仅 TRACKING/EVALUATED 可转派"。
 * <b>待业务确认的 gap</b>：文档第 9 条说"任何状态可强行干预转派"，
 * 代码限制为 TRACKING/EVALUATED。本测试锁定代码现状，gap 在审计文档中标注。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenderTransferService 转派（CO-261 通知 + 状态收口契约）")
class TenderTransferServiceTest {

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderAssignmentRecordRepository assignmentRecordRepository;
    @Mock
    private TenderAuditService tenderAuditService;
    @Mock
    private TenderAssignmentNotifier assignmentNotifier;

    private TenderTransferService service;

    @BeforeEach
    void setUp() {
        service = new TenderTransferService(
                tenderRepository, userRepository, assignmentRecordRepository,
                tenderAuditService, assignmentNotifier);
    }

    @Test
    @DisplayName("CO-261: 转派成功后给新负责人发站内通知")
    void transfer_ShouldNotifyNewOwner() {
        Tender tender = Tender.builder()
                .id(1L).title("测试标讯").status(Tender.Status.TRACKING)
                .projectManagerId(10L).projectManagerName("旧负责人").build();
        User newOwner = User.builder().id(20L).fullName("新负责人").departmentName("销售部").enabled(true).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(newOwner));
        when(userRepository.findById(99L)).thenReturn(Optional.of(
                User.builder().id(99L).fullName("操作人").build()));

        service.transfer(1L, 20L, 99L);

        // CO-261: 转派成功后必须通知新负责人（Notifier 内部保证异常不影响主事务）
        verify(assignmentNotifier).notifyTransferred(any(Tender.class), anyLong(), anyString(), anyString(), anyLong());
    }

    // ====================================================================
    // 状态收口契约测试（飞书《标讯中心·权限矩阵》第 9 条）
    // 锁定当前实现：仅 TRACKING/EVALUATED 可转派
    // ====================================================================

    @Test
    @DisplayName("状态收口：EVALUATED 状态可转派（当前实现允许）")
    void transfer_evaluatedStatus_allows() {
        Tender tender = Tender.builder()
                .id(1L).title("已评估标讯").status(Tender.Status.EVALUATED)
                .projectManagerId(10L).build();
        User newOwner = User.builder().id(20L).fullName("新负责人").enabled(true).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(newOwner));
        when(userRepository.findById(99L)).thenReturn(Optional.of(
                User.builder().id(99L).fullName("操作人").build()));

        service.transfer(1L, 20L, 99L); // 不抛异常即通过

        verify(tenderRepository).save(any(Tender.class));
    }

    @Test
    @DisplayName("状态收口：PENDING_ASSIGNMENT 状态不可转派 → 抛异常")
    void transfer_pendingAssignment_throws() {
        Tender tender = Tender.builder()
                .id(1L).title("待分配标讯").status(Tender.Status.PENDING_ASSIGNMENT)
                .projectManagerId(10L).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.transfer(1L, 20L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标讯状态已变更，无法转派");
    }

    @Test
    @DisplayName("状态收口：BIDDING 状态不可转派 → 抛异常（已立项进入投标流程）")
    void transfer_bidding_throws() {
        Tender tender = Tender.builder()
                .id(1L).title("投标中标讯").status(Tender.Status.BIDDING)
                .projectManagerId(10L).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.transfer(1L, 20L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标讯状态已变更，无法转派");
    }

    @Test
    @DisplayName("状态收口：WON 状态不可转派 → 抛异常")
    void transfer_won_throws() {
        Tender tender = Tender.builder()
                .id(1L).title("已中标标讯").status(Tender.Status.WON)
                .projectManagerId(10L).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));

        assertThatThrownBy(() -> service.transfer(1L, 20L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标讯状态已变更，无法转派");
    }
}
