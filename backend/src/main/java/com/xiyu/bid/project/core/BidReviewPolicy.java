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
 *
 * <p>身份约束（2026-06-07 根因修复，IJSTZG）：</p>
 * <ul>
 *   <li>提交人不能审批/驳回自己提交的标书</li>
 *   <li>仅指派的审核人（{@code reviewerId == currentUserId}）可执行审批/驳回</li>
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
            return Decision.deny(Decision.Cause.STATE, "该标书正在审核中，请等待审核结果");
        }
        if (current == BidReviewStatus.APPROVED) {
            return Decision.deny(Decision.Cause.STATE, "该标书已审核通过，无需重复提交");
        }
        return Decision.permit();
    }

    /**
     * 校验审核是否可以通过。
     *
     * <p>状态校验：仅 REVIEWING 状态可被审核通过。</p>
     * <p>身份校验（IJSTZG 根因修复 2026-06-07）：
     * 提交人不能审批自己提交的标书；
     * 仅指派的审核人可以审批。</p>
     *
     * @param current        当前状态
     * @param submittedBy    提交审核的用户 ID（来自 {@code BidDocumentReviewEntity.submittedBy}）
     * @param reviewerId     指派的审核人 ID（来自 {@code BidDocumentReviewEntity.reviewerId}）
     * @param currentUserId  当前操作用户 ID（来自 {@code @AuthenticationPrincipal}）
     * @return 允许或拒绝决定
     */
    public static Decision canApprove(final BidReviewStatus current,
                                      final Long submittedBy,
                                      final Long reviewerId,
                                      final Long currentUserId) {
        if (current == null) {
            return Decision.deny(Decision.Cause.STATE, "尚未提交审核，无法通过");
        }
        if (current == BidReviewStatus.APPROVED) {
            return Decision.deny(Decision.Cause.STATE, "该标书已审核通过");
        }
        if (current == BidReviewStatus.REJECTED) {
            return Decision.deny(Decision.Cause.STATE, "该标书已被驳回，请重新提交后再审核");
        }
        if (currentUserId == null) {
            return Decision.deny(Decision.Cause.IDENTITY, "未识别操作者，无法审核");
        }
        if (submittedBy != null && submittedBy.equals(currentUserId)) {
            return Decision.deny(Decision.Cause.IDENTITY, "提交人不能审批自己提交的标书");
        }
        if (reviewerId != null && !reviewerId.equals(currentUserId)) {
            return Decision.deny(Decision.Cause.IDENTITY, "仅指派的审核人可以审批");
        }
        return Decision.permit();
    }

    /**
     * 校验审核是否可以驳回。
     *
     * <p>状态校验：仅 REVIEWING 状态可被驳回，且必须填写驳回原因。</p>
     * <p>身份校验：与 {@link #canApprove} 同等约束（防止自我审批与跨人审批）。</p>
     *
     * @param current        当前状态
     * @param reason         驳回原因
     * @param submittedBy    提交审核的用户 ID
     * @param reviewerId     指派的审核人 ID
     * @param currentUserId  当前操作用户 ID
     * @return 允许或拒绝决定
     */
    public static Decision canReject(final BidReviewStatus current,
                                     final String reason,
                                     final Long submittedBy,
                                     final Long reviewerId,
                                     final Long currentUserId) {
        if (current == null) {
            return Decision.deny(Decision.Cause.STATE, "尚未提交审核，无法驳回");
        }
        if (current == BidReviewStatus.APPROVED) {
            return Decision.deny(Decision.Cause.STATE, "该标书已审核通过，无法驳回");
        }
        if (current == BidReviewStatus.REJECTED) {
            return Decision.deny(Decision.Cause.STATE, "该标书已被驳回");
        }
        if (reason == null || reason.isBlank()) {
            return Decision.deny(Decision.Cause.STATE, "驳回原因不能为空");
        }
        if (currentUserId == null) {
            return Decision.deny(Decision.Cause.IDENTITY, "未识别操作者，无法驳回");
        }
        if (submittedBy != null && submittedBy.equals(currentUserId)) {
            return Decision.deny(Decision.Cause.IDENTITY, "提交人不能驳回自己提交的标书");
        }
        if (reviewerId != null && !reviewerId.equals(currentUserId)) {
            return Decision.deny(Decision.Cause.IDENTITY, "仅指派的审核人可以驳回");
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
            return Decision.deny(Decision.Cause.STATE, "标书尚未审核通过，无法提交投标");
        }
        if (current == BidReviewStatus.REJECTED) {
            return Decision.deny(Decision.Cause.STATE, "标书已被驳回，请修改后重新提交审核");
        }
        return Decision.permit();
    }

    /**
     * 审核状态流转决策结果。
     *
     * <p>{@code cause} 用于服务层映射为合适的 HTTP 状态码：</p>
     * <ul>
     *   <li>{@link Cause#STATE} → 409 Conflict（资源状态不允许该操作）</li>
     *   <li>{@link Cause#IDENTITY} → 403 Forbidden（无权限/非指派人/自我审批）</li>
     * </ul>
     *
     * @param allowed 是否允许
     * @param cause   拒绝原因类型（allowed=true 时为 null）
     * @param reason  拒绝原因描述（allowed=true 时为 null）
     */
    public record Decision(boolean allowed, Cause cause, String reason) {
        /**
         * 拒绝原因类型。供编排层选择 HTTP 状态码。
         */
        public enum Cause {
            /** 资源状态机不允许该操作（资源已处于目标状态） */
            STATE,
            /** 操作者身份不符（自审/非指派人/无身份） */
            IDENTITY
        }

        public static Decision permit() {
            return new Decision(true, null, null);
        }

        public static Decision deny(Cause cause, String reason) {
            return new Decision(false, cause, reason);
        }
    }
}
