package com.xiyu.bid.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloseWarehouseRequest(
        @NotBlank(message = "关仓原因不能为空")
        @Size(max = 500, message = "关仓原因不能超过500字符")
        String reason
) {}
