package com.xiyu.bid.project.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标书审核视图 DTO。
 *
 * <p>CO-XXX 修复：{@code BidReviewAppService} 的 submitForReview/approveBid/rejectBid
 * 原为 void 方法，导致 {@code AuditableAspect} 无法从返回值提取 projectId，项目动态丢失
 * "提交标书审核/审核通过/审核驳回"记录。改为返回本 DTO，切面通过 {@link #getProjectId()}
 * 反射提取 projectId（Lombok {@code @Data} 自动生成）。
 *
 * <p>{@code projectId} 字段标注 {@link JsonIgnore}，不参与 Jackson 序列化，
 * 避免 API 响应暴露内部字段（前端通过 ProjectDraftingViewDto 获取项目信息）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidDocumentReviewViewDto {

    /** 审核记录 ID */
    private Long id;

    /**
     * 项目 ID（供 AuditableAspect 反射提取，不参与 JSON 序列化）。
     *
     * <p>{@link JsonIgnore} 标注在字段上，Jackson 会忽略该字段的 getter/setter，
     * 不参与 API 序列化/反序列化。AuditableAspect 通过反射调用 {@code getProjectId()}
     * （Lombok {@code @Data} 自动生成）提取 projectId。
     */
    @JsonIgnore
    private Long projectId;

    /** 审核状态：REVIEWING / APPROVED / REJECTED */
    private String status;

    /** 主审核人 ID */
    private Long reviewerId;

    /** 提交审核的用户 ID */
    private Long submittedBy;

    /** 驳回原因 */
    private String rejectReason;

    /** 审核时间 */
    private LocalDateTime reviewedAt;
}
