package com.xiyu.bid.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for returning a borrowed Platform Account with mandatory password change. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnAccountRequest {

    /** New password (min 6 chars) — mandatory per blueprint rule. */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度不能少于6位")
    private String newPassword;

    /** Optional return remarks. */
    private String remarks;
}
