package com.xiyu.bid.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * PATCH /api/admin/api-keys/{id} 请求体。
 * scopes 必填（全量替换），name 可选（为 null 或空白时保留原名）。
 */
public record UpdateApiKeyRequest(
    @Size(max = 200) String name,
    @NotEmpty List<@NotBlank String> scopes
) {}