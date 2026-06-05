package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BarSiteStatusUpdateRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}
