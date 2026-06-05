package com.xiyu.bid.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 立项审核驳回请求。
 * 产品蓝图 V1.1 §4.3：驳回必须填写原因。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiationRejectionRequest {

    @NotBlank(message = "驳回原因不能为空")
    private String rejectionReason;
}
