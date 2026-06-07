package com.xiyu.bid.apikey.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CreateApiKeyResponse(
    Long id,
    String secret,
    String name,
    List<String> scopes,
    LocalDateTime expiresAt,
    String warning
) {}
