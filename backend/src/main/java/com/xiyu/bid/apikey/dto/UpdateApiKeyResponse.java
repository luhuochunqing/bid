package com.xiyu.bid.apikey.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PATCH /api/admin/api-keys/{id} 响应体。
 * 仅返回更新后的关键信息；status/createdBy/createdAt 不受本接口影响。
 */
public record UpdateApiKeyResponse(
    Long id,
    String name,
    List<String> scopes,
    LocalDateTime expiresAt
) {}