package com.xiyu.bid.apikey.dto;

import com.xiyu.bid.apikey.entity.ApiKey;
import com.xiyu.bid.apikey.entity.ApiKeyStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ApiKeyResponse(
    Long id,
    String name,
    List<String> scopes,
    ApiKeyStatus status,
    String createdBy,
    LocalDateTime expiresAt,
    LocalDateTime createdAt
) {
    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(
            key.getId(), key.getName(),
            key.getScopes() == null ? List.of() : List.of(key.getScopes().split(",")),
            key.getStatus(), key.getCreatedBy(), key.getExpiresAt(), key.getCreatedAt()
        );
    }
}
