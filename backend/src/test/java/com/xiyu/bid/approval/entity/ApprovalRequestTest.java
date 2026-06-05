package com.xiyu.bid.approval.entity;

import com.xiyu.bid.approval.enums.ApprovalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApprovalRequest实体测试
 */
@DisplayName("ApprovalRequest实体测试")
class ApprovalRequestTest {

    private ApprovalRequest approvalRequest;

    @BeforeEach
    void setUp() {
        approvalRequest = ApprovalRequest.builder()
                .id(UUID.randomUUID())
                .projectId(1L)
                .projectName("测试项目")
                .approvalType("BID_DOCUMENT")
                .status(ApprovalStatus.PENDING)
                .requesterId(100L)
                .requesterName("张三")
                .currentApproverId(200L)
                .currentApproverName("李四")
                .priority(1)
                .title("投标文档审批")
                .description("请审批投标文档")
                .submittedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("应该可以审批待审批状态的请求")
    void canBeApproved_ShouldReturnTrue_WhenStatusIsPending() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        assertTrue(approvalRequest.canBeApproved());
    }

    @Test
    @DisplayName("不应该可以审批已通过状态的请求")
    void canBeApproved_ShouldReturnFalse_WhenStatusIsApproved() {
        approvalRequest.setStatus(ApprovalStatus.APPROVED);
        assertFalse(approvalRequest.canBeApproved());
    }

    @Test
    @DisplayName("不应该可以审批已驳回状态的请求")
    void canBeApproved_ShouldReturnFalse_WhenStatusIsRejected() {
        approvalRequest.setStatus(ApprovalStatus.REJECTED);
        assertFalse(approvalRequest.canBeApproved());
    }

    @Test
    @DisplayName("不应该可以审批已取消状态的请求")
    void canBeApproved_ShouldReturnFalse_WhenStatusIsCancelled() {
        approvalRequest.setStatus(ApprovalStatus.CANCELLED);
        assertFalse(approvalRequest.canBeApproved());
    }

    @Test
    @DisplayName("申请人应该可以取消待审批的请求")
    void canBeCancelledBy_ShouldReturnTrue_WhenStatusIsPendingAndUserIsRequester() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        approvalRequest.setRequesterId(100L);
        assertTrue(approvalRequest.canBeCancelledBy(100L));
    }

    @Test
    @DisplayName("非申请人不应该可以取消请求")
    void canBeCancelledBy_ShouldReturnFalse_WhenUserIsNotRequester() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        approvalRequest.setRequesterId(100L);
        assertFalse(approvalRequest.canBeCancelledBy(200L));
    }

    @Test
    @DisplayName("不应该取消已完成的请求")
    void canBeCancelledBy_ShouldReturnFalse_WhenStatusIsNotPending() {
        approvalRequest.setStatus(ApprovalStatus.APPROVED);
        approvalRequest.setRequesterId(100L);
        assertFalse(approvalRequest.canBeCancelledBy(100L));
    }

    @Test
    @DisplayName("应该检测到超期")
    void isOverdue_ShouldReturnTrue_WhenDueDateIsPastAndStatusIsPending() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        approvalRequest.setDueDate(LocalDateTime.now().minusHours(1));
        assertTrue(approvalRequest.isOverdue());
    }

    @Test
    @DisplayName("不应该检测超期当状态不是待审批")
    void isOverdue_ShouldReturnFalse_WhenStatusIsNotPending() {
        approvalRequest.setStatus(ApprovalStatus.APPROVED);
        approvalRequest.setDueDate(LocalDateTime.now().minusHours(1));
        assertFalse(approvalRequest.isOverdue());
    }

    @Test
    @DisplayName("不应该检测超期当截止日期未到")
    void isOverdue_ShouldReturnFalse_WhenDueDateIsFuture() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        approvalRequest.setDueDate(LocalDateTime.now().plusHours(1));
        assertFalse(approvalRequest.isOverdue());
    }

    @Test
    @DisplayName("应该检测到临近截止")
    void isNearDueDate_ShouldReturnTrue_WhenDueDateIsWithin24Hours() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        approvalRequest.setDueDate(LocalDateTime.now().plusHours(12));
        assertTrue(approvalRequest.isNearDueDate());
    }

    @Test
    @DisplayName("不应该检测临近截止当超过24小时")
    void isNearDueDate_ShouldReturnFalse_WhenDueDateIsMoreThan24HoursAway() {
        approvalRequest.setStatus(ApprovalStatus.PENDING);
        approvalRequest.setDueDate(LocalDateTime.now().plusHours(30));
        assertFalse(approvalRequest.isNearDueDate());
    }

    @Test
    @DisplayName("默认值应该正确设置")
    void defaultValues_ShouldBeSetCorrectly() {
        ApprovalRequest request = new ApprovalRequest();
        request.onCreate();
        assertEquals(ApprovalStatus.PENDING, request.getStatus());
        assertEquals(0, request.getPriority());
        assertFalse(request.getIsRead());
    }
}
