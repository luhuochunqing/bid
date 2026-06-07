// Input: 当前审核状态 + 请求的目标操作
// Output: Decision(allowed/reason) — 纯函数，无副作用
// Pos: project/core/ - pure core policy, no Spring/JPA
package com.xiyu.bid.project.core;

/**
 * 标书审核状态流转校验策略。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。</p>
 *
 * <p>允许的流转：</p>
 * <ul>
 *   <li>null → REVIEWING（首次提交审核）</li>
 *   <li>REJECTED → REVIEWING（驳回后重新提交）</li>
 *   <li>REVIEWING → APPROVED（审核通过）</li>
 *   <li>REVIEWING → REJECTED（审核驳回）</li>
 * </ul>
 */
public final class BidReviewPolicy {

    private BidReviewPolicy() {
    }

    /**
     * 校验从当前状态能否提交审核（变为 REVIEWING）。
     *
     * @param current 当前状态，可为 null
     * @return 允许或拒绝决定
     */
    public static Decision canSubmitReview(final BidReviewStatus current) {
        if (current == BidReviewStatus.REVIEWING) {
            return Decision.deny("该标书正在审核中，请等待审核结果");
        }
        if (current == BidReviewStatus.APPROVED) {
            return Decision.deny("该标书已审核通过，无需重复提交");
        }
        return Decision.permit();
    }

    /**
     * 校验审核是否可以通过。
     *
     * @param current 当前状态
     * @return 允许或拒绝决定
     */
    public static Decision canApprove(final BidReviewStatus current) {
        if (current == null) {
            return Decision.deny("尚未提交审核，无法通过");
        }
        if (current == BidReviewStatus.APPROVED) {
            return Decision.deny("该标书已审核通过");
        }
        if (current == BidReviewStatus.REJECTED) {
            return Decision.deny("该标书已被驳回，请重新提交后再审核");
        }
        return Decision.permit();
    }

    /**
     * 校验审核是否可以驳回。
     *
     * @param current 当前状态
     * @param reason  驳回原因
     * @return 允许或拒绝决定
     */
    public static Decision canReject(final BidReviewStatus current, final String reason) {
        if (current == null) {
            return Decision.deny("尚未提交审核，无法驳回");
        }
        if (current == BidReviewStatus.APPROVED) {
            return Decision.deny("该标书已审核通过，无法驳回");
        }
        if (current == BidReviewStatus.REJECTED) {
            return Decision.deny("该标书已被驳回");
        }
        if (reason == null || reason.isBlank()) {
            return Decision.deny("驳回原因不能为空");
        }
        return Decision.permit();
    }

    /**
     * 校验审核通过后是否可以提交投标。
     *
     * @param current 当前审核状态
     * @return 允许或拒绝决定
     */
    public static Decision canSubmitBid(final BidReviewStatus current) {
        if (current == null || current == BidReviewStatus.REVIEWING) {
            return Decision.deny("标书尚未审核通过，无法提交投标");
        }
        if (current == BidReviewStatus.REJECTED) {
            return Decision.deny("标书已被驳回，请修改后重新提交审核");
        }
        return Decision.permit();
    }

    /**
     * 审核状态流转决策结果。
     *
     * @param allowed 是否允许
     * @param reason  拒绝原因（allowed=true 时为 null）
     */
    public record Decision(boolean allowed, String reason) {
        public static Decision permit() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
