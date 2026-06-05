package com.xiyu.bid.notification.outbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WeComBindingRequest(
    @NotBlank
    @Size(max = 64)
    @Pattern(
        regexp = "^[A-Za-z0-9._\\-]+$",
        message = "企微 userid 只允许字母、数字、点、下划线、短横线"
    )
    String wecomUserId
) {
}
