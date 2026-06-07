package com.xiyu.bid.approval.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApprovalStatus枚举测试
 */
@DisplayName("ApprovalStatus枚举测试")
class ApprovalStatusTest {

    @Test
    @DisplayName("已通过应该是最终状态")
    void isFinalStatus_ShouldReturnTrue_ForApproved() {
        assertTrue(ApprovalStatus.APPROVED.isFinalStatus());
    }

    @Test
    @DisplayName("已驳回应该是最终状态")
    void isFinalStatus_ShouldReturnTrue_ForRejected() {
        assertTrue(ApprovalStatus.REJECTED.isFinalStatus());
    }

    @Test
    @DisplayName("已取消应该是最终状态")
    void isFinalStatus_ShouldReturnTrue_ForCancelled() {
        assertTrue(ApprovalStatus.CANCELLED.isFinalStatus());
    }

    @Test
    @DisplayName("待审批不应该是最终状态")
    void isFinalStatus_ShouldReturnFalse_ForPending() {
        assertFalse(ApprovalStatus.PENDING.isFinalStatus());
    }

    @Test
    @DisplayName("待审批可以转换到已通过")
    void canTransitionTo_ShouldReturnTrue_FromPendingToApproved() {
        assertTrue(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.APPROVED));
    }

    @Test
    @DisplayName("待审批可以转换到已驳回")
    void canTransitionTo_ShouldReturnTrue_FromPendingToRejected() {
        assertTrue(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.REJECTED));
    }

    @Test
    @DisplayName("待审批可以转换到已取消")
    void canTransitionTo_ShouldReturnTrue_FromPendingToCancelled() {
        assertTrue(ApprovalStatus.PENDING.canTransitionTo(ApprovalStatus.CANCELLED));
    }

    @Test
    @DisplayName("最终状态不能转换")
    void canTransitionTo_ShouldReturnFalse_FromFinalStatus() {
        assertFalse(ApprovalStatus.APPROVED.canTransitionTo(ApprovalStatus.PENDING));
        assertFalse(ApprovalStatus.REJECTED.canTransitionTo(ApprovalStatus.PENDING));
        assertFalse(ApprovalStatus.CANCELLED.canTransitionTo(ApprovalStatus.PENDING));
    }

    @Test
    @DisplayName("状态描述应该正确")
    void descriptions_ShouldBeCorrect() {
        assertEquals("待审批", ApprovalStatus.PENDING.getDescription());
        assertEquals("已通过", ApprovalStatus.APPROVED.getDescription());
        assertEquals("已驳回", ApprovalStatus.REJECTED.getDescription());
        assertEquals("已取消", ApprovalStatus.CANCELLED.getDescription());
    }
}
