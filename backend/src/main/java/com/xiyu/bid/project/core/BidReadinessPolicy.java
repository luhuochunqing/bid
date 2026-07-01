// Input: 任务状态列表 + 是否已上传标书文件
// Output: Decision(ready/reason) - 标书可提交审核/投标的纯核心闸门
// Pos: project/core/ - 纯规则，无 Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.List;

/**
 * PRD §3.2.3 标书编制提交闸门：所有任务已完成 + 标书文件已上传。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。所有方法返回 {@link Decision} 值，
 * 编排层按 {@link Decision.Cause} 映射 HTTP 状态码（STATE→409）。</p>
 *
 * <p>本类提供两个语义不同的闸门方法，避免业务入口错配闸门（CO-400 教训）：</p>
 * <ul>
 *   <li>{@link #checkBidDocumentUploaded(boolean)} — 仅校验标书文件已上传，
 *       供 {@code submitForReview}（提交标书审核）使用。
 *       业务语义：发起审核时标书可能仍在编制，任务完成与否是审核人的判断，不是闸门。</li>
 *   <li>{@link #checkBidSubmissionReady(List, boolean)} — 校验任务全完成 + 标书文件已上传，
 *       供 {@code submitBid}（推进到评标阶段）使用。
 *       业务语义：推进阶段是重大阶段流转，要求所有任务终态完成。</li>
 * </ul>
 *
 * <p><b>CO-400 防复发约束</b>：修改本类时必须检查 submitBid / submitForReview 两个调用点
 * 是否仍然按上述语义分工调用对应方法，不得混淆。详见
 * {@code docs/lessons/lessons-learned.md §25}。</p>
 */
public final class BidReadinessPolicy {

    /** 标书文件在项目文档表中的 documentCategory 取值（CO-420 标准枚举名）。 */
    public static final String BID_DOCUMENT_CATEGORY = "BID";

    private BidReadinessPolicy() {
    }

    /**
     * 仅校验标书文件已上传。
     *
     * <p>供 {@code ProjectDraftingService.submitForReview}（提交标书给审核人审核）使用。
     * 业务语义：发起审核时，标书可能仍在编制，任务完成与否是审核人的判断，不是闸门。</p>
     *
     * @param hasBidDocument 是否存在标书文件（{@code documentCategory=BID}）
     * @return 允许或拒绝决定 + 拒绝原因
     */
    public static Decision checkBidDocumentUploaded(boolean hasBidDocument) {
        if (!hasBidDocument) {
            return Decision.deny(Decision.Cause.STATE, "尚未上传标书文件");
        }
        return Decision.permit();
    }

    /**
     * 校验任务全完成 + 标书文件已上传。
     *
     * <p>供 {@code ProjectDraftingService.submitBid}（推进到评标阶段）使用。
     * 业务语义：推进阶段是重大阶段流转，要求所有任务终态完成。</p>
     *
     * <p>任务问题优先级高：先报告"任务未完成"，再检查标书文件。</p>
     *
     * @param taskStates     项目下所有任务的状态快照（来自 shell 层）
     * @param hasBidDocument 是否存在标书文件（{@code documentCategory=BID}）
     * @return 允许或拒绝决定 + 拒绝原因
     */
    public static Decision checkBidSubmissionReady(List<AllTasksCompletedPolicy.TaskState> taskStates,
                                                   boolean hasBidDocument) {
        AllTasksCompletedPolicy.Decision taskDecision = AllTasksCompletedPolicy.decide(taskStates);
        if (!taskDecision.allowed()) {
            int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) taskDecision).incompleteCount();
            return Decision.deny(Decision.Cause.STATE,
                    "仍有 " + incomplete + " 个任务未完成");
        }
        return checkBidDocumentUploaded(hasBidDocument);
    }

    /**
     * 闸门决策结果。
     */
    public record Decision(boolean allowed, Cause cause, String reason) {

        public enum Cause {
            STATE
        }

        public static Decision permit() {
            return new Decision(true, null, null);
        }

        public static Decision deny(Cause cause, String reason) {
            return new Decision(false, cause, reason);
        }
    }
}
