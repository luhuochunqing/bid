package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaApprovalRequest {
    @NotBlank(message = "审批意见不能为空")
    private String comment;
}
