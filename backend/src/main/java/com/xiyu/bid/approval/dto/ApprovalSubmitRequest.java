package com.xiyu.bid.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交审批请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalSubmitRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 项目名称 (冗余)
     */
    private String projectName;

    /**
     * 审批类型
     */
    @NotBlank(message = "审批类型不能为空")
    private String approvalType;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 描述/说明
     */
    private String description;

    /**
     * 优先级 (0-普通, 1-紧急, 2-非常紧急)
     */
    @Builder.Default
    private Integer priority = 0;

    /**
     * 指定审批人ID (可选，不指定则按默认规则)
     */
    private Long approverId;

    /**
     * 预期完成时间
     */
    private LocalDateTime dueDate;

    /**
     * 附件ID列表
     */
    private List<Long> attachmentIds;
}
