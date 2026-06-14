package com.xiyu.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionResult {

    private AuthResponse authResponse;
    private String refreshToken;
    /**
     * H13 根治 (2026-06-14): access token 不再放 {@link AuthResponse#getToken()} (响应 body),
     * 改由 controller 通过 HttpOnly cookie 投递。此字段承载 access token 字符串供 controller
     * 构造 Set-Cookie; {@code @JsonIgnore} 确保它绝不进响应 body (否则 XSS 仍可从响应盗取).
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String accessToken;
}
