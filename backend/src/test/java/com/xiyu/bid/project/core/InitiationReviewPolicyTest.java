// Input: 立项审核请求 (approve/reject/submit)
// Output: JUnit5 断言覆盖 InitiationReviewPolicy 全部分支
// Pos: backend test source - 纯 JUnit5
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import com.xiyu.bid.project.dto.InitiationApprovalRequest;
import com.xiyu.bid.project.dto.InitiationRejectionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 立项审核规则单元测试。蓝图 V1.1 §4.3。
 */
class InitiationReviewPolicyTest {

    private InitiationApprovalRequest validApproval() {
        return InitiationApprovalRequest.builder()
                .primaryLeadUserId(100L)
                .build();
    }

    private InitiationRejectionRequest validRejection() {
        return InitiationRejectionRequest.builder()
                .rejectionReason("信息不完整")
                .build();
    }

    @Test
    void approve_pendingReview_withLead_allowed() {
        var d = InitiationReviewPolicy.validateApproval(
                InitiationReviewStatus.PENDING_REVIEW, validApproval());
        assertTrue(d.allowed());
    }

    @Test
    void approve_draftStatus_denied() {
        var d = InitiationReviewPolicy.validateApproval(
                InitiationReviewStatus.DRAFT, validApproval());
        assertFalse(d.allowed());
        assertInstanceOf(InitiationReviewPolicy.Decision.Deny.class, d);
    }

    @Test
    void approve_alreadyApproved_denied() {
        var d = InitiationReviewPolicy.validateApproval(
                InitiationReviewStatus.APPROVED, validApproval());
        assertFalse(d.allowed());
    }

    @Test
    void approve_rejectedStatus_denied() {
        var d = InitiationReviewPolicy.validateApproval(
                InitiationReviewStatus.REJECTED, validApproval());
        assertFalse(d.allowed());
    }

    @Test
    void approve_nullRequest_denied() {
        var d = InitiationReviewPolicy.validateApproval(
                InitiationReviewStatus.PENDING_REVIEW, null);
        assertFalse(d.allowed());
    }

    @Test
    void approve_missingPrimaryLead_denied() {
        var req = InitiationApprovalRequest.builder().build(); // 无 primaryLeadUserId
        var d = InitiationReviewPolicy.validateApproval(
                InitiationReviewStatus.PENDING_REVIEW, req);
        assertFalse(d.allowed());
    }

    @Test
    void reject_pendingReview_withReason_allowed() {
        var d = InitiationReviewPolicy.validateRejection(
                InitiationReviewStatus.PENDING_REVIEW, validRejection());
        assertTrue(d.allowed());
    }

    @Test
    void reject_draftStatus_denied() {
        var d = InitiationReviewPolicy.validateRejection(
                InitiationReviewStatus.DRAFT, validRejection());
        assertFalse(d.allowed());
    }

    @Test
    void reject_blankReason_denied() {
        var req = InitiationRejectionRequest.builder().rejectionReason("   ").build();
        var d = InitiationReviewPolicy.validateRejection(
                InitiationReviewStatus.PENDING_REVIEW, req);
        assertFalse(d.allowed());
    }

    @Test
    void reject_nullReason_denied() {
        var req = InitiationRejectionRequest.builder().rejectionReason(null).build();
        var d = InitiationReviewPolicy.validateRejection(
                InitiationReviewStatus.PENDING_REVIEW, req);
        assertFalse(d.allowed());
    }

    @Test
    void reject_nullRequest_denied() {
        var d = InitiationReviewPolicy.validateRejection(
                InitiationReviewStatus.PENDING_REVIEW, null);
        assertFalse(d.allowed());
    }

    @Test
    void submit_draftStatus_allowed() {
        assertTrue(InitiationReviewPolicy.validateSubmit(InitiationReviewStatus.DRAFT).allowed());
    }

    @Test
    void submit_rejectedStatus_allowed() {
        // 驳回后可以重新提交
        assertTrue(InitiationReviewPolicy.validateSubmit(InitiationReviewStatus.REJECTED).allowed());
    }

    @Test
    void submit_pendingReview_denied() {
        assertFalse(InitiationReviewPolicy.validateSubmit(InitiationReviewStatus.PENDING_REVIEW).allowed());
    }

    @Test
    void submit_approved_denied() {
        assertFalse(InitiationReviewPolicy.validateSubmit(InitiationReviewStatus.APPROVED).allowed());
    }
}
