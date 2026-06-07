// Input: 项目 id、leads 信息、审核状态、闸门状态
// Output: ProjectDraftingViewDto（标书制作阶段全量视图）
// Pos: project/dto/ - value object
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectDraftingViewDto {
    private Long projectId;
    private Long primaryLeadUserId;
    private Long secondaryLeadUserId;
    private Integer incompleteTaskCount;
    private Boolean gateReady;

    // ── 标书审核字段 ──────────────────────────────────────────────────────
    /** 审核状态：null / REVIEWING / APPROVED / REJECTED */
    private String reviewStatus;
    /** 审核人用户 ID */
    private Long reviewerId;
    /** 审核人名称 */
    private String reviewerName;
    /** 驳回原因 */
    private String rejectReason;

    /** 是否已提交投标（推进到评标阶段） */
    private Boolean bidSubmitted;
}
