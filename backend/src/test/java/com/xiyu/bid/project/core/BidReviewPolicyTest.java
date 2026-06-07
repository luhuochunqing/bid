// Input: 各种状态组合与操作请求
// Output: 验证 BidReviewPolicy 四类方法在正常/异常路径下的 Decision
// Pos: Test/核心策略测试
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标书审核策略单元测试。
 * <p>覆盖 {@link BidReviewPolicy} 所有四个 can* 方法的正常与拒绝路径。</p>
 */
class BidReviewPolicyTest {

    // ── canSubmitReview ──────────────────────────────────────────────────

    @Test
    void canSubmitReview_whenNull_shouldAllow() {
        var result = BidReviewPolicy.canSubmitReview(null);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void canSubmitReview_whenRejected_shouldAllow() {
        var result = BidReviewPolicy.canSubmitReview(BidReviewStatus.REJECTED);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void canSubmitReview_whenReviewing_shouldDeny() {
        var result = BidReviewPolicy.canSubmitReview(BidReviewStatus.REVIEWING);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("审核中");
    }

    @Test
    void canSubmitReview_whenApproved_shouldDeny() {
        var result = BidReviewPolicy.canSubmitReview(BidReviewStatus.APPROVED);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("已审核通过");
    }

    // ── canApprove ──────────────────────────────────────────────────────

    @Test
    void canApprove_whenNull_shouldDeny() {
        var result = BidReviewPolicy.canApprove(null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("尚未提交审核");
    }

    @Test
    void canApprove_whenReviewing_shouldAllow() {
        var result = BidReviewPolicy.canApprove(BidReviewStatus.REVIEWING);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void canApprove_whenApproved_shouldDeny() {
        var result = BidReviewPolicy.canApprove(BidReviewStatus.APPROVED);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("已审核通过");
    }

    @Test
    void canApprove_whenRejected_shouldDeny() {
        var result = BidReviewPolicy.canApprove(BidReviewStatus.REJECTED);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("被驳回");
    }

    // ── canReject ───────────────────────────────────────────────────────

    @Test
    void canReject_whenNull_shouldDeny() {
        var result = BidReviewPolicy.canReject(null, "原因");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("尚未提交审核");
    }

    @Test
    void canReject_whenReviewing_shouldAllow() {
        var result = BidReviewPolicy.canReject(BidReviewStatus.REVIEWING, "内容不符");
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void canReject_whenApproved_shouldDeny() {
        var result = BidReviewPolicy.canReject(BidReviewStatus.APPROVED, "原因");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("已审核通过");
    }

    @Test
    void canReject_whenRejected_shouldDeny() {
        var result = BidReviewPolicy.canReject(BidReviewStatus.REJECTED, "原因");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("已被驳回");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "\t"})
    void canReject_whenReasonBlank_shouldDeny(String blankReason) {
        var result = BidReviewPolicy.canReject(BidReviewStatus.REVIEWING, blankReason);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("驳回原因不能为空");
    }

    // ── canSubmitBid ───────────────────────────────────────────────────

    @Test
    void canSubmitBid_whenNull_shouldDeny() {
        var result = BidReviewPolicy.canSubmitBid(null);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("尚未审核通过");
    }

    @Test
    void canSubmitBid_whenReviewing_shouldDeny() {
        var result = BidReviewPolicy.canSubmitBid(BidReviewStatus.REVIEWING);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("尚未审核通过");
    }

    @Test
    void canSubmitBid_whenRejected_shouldDeny() {
        var result = BidReviewPolicy.canSubmitBid(BidReviewStatus.REJECTED);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("被驳回");
    }

    @Test
    void canSubmitBid_whenApproved_shouldAllow() {
        var result = BidReviewPolicy.canSubmitBid(BidReviewStatus.APPROVED);
        assertThat(result.allowed()).isTrue();
    }
}
