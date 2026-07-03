// Input: BidReviewAssignmentEntity
// Output: ReviewerDecisionDto - 审核人决策快照（CO-484 多人审核）
// Pos: project/dto/ - value object
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标书审核人决策快照（CO-484 多人审核）。
 * <p>每个审核人的 ID / 姓名 / 决策 / 驳回原因。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewerDecisionDto {
    /** 审核人用户 ID */
    private Long reviewerId;
    /** 审核人姓名 */
    private String reviewerName;
    /** 决策：null(未决) / APPROVED / REJECTED */
    private String decision;
    /** 驳回原因（驳回时填） */
    private String comment;
}
