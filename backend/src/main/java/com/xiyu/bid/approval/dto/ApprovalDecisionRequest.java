package com.xiyu.bid.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批决策请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDecisionRequest {

    /**
     * 审批意见/备注
     */
    @NotBlank(message = "审批意见不能为空")
    private String comment;

    /**
     * 是否要求重新提交
     */
    @Builder.Default
    private Boolean requireResubmit = false;
}
