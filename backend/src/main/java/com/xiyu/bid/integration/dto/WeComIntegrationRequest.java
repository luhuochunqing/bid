package com.xiyu.bid.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for saving/updating WeCom integration configuration.
 * Bean Validation annotations enforce field constraints at the controller boundary.
 */
public record WeComIntegrationRequest(

        @NotBlank(message = "corpId 不能为空")
        @Size(max = 64, message = "corpId 长度不能超过 64 个字符")
        String corpId,

        @NotBlank(message = "agentId 不能为空")
        @Pattern(regexp = "\\d{1,32}", message = "agentId 必须为纯数字字符串且不超过 32 位")
        String agentId,

        @NotBlank(message = "corpSecret 不能为空")
        String corpSecret,

        boolean ssoEnabled,

        boolean messageEnabled,

        String notifyUserIds
) {
}
