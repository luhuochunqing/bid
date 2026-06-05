package com.xiyu.bid.dto;

import java.time.LocalDateTime;

/**
 * DTO for session information
 * Used to display active user sessions
 */
public record SessionDTO(
    Long id,
    String deviceInfo,
    String ipAddress,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    LocalDateTime lastSeenAt
) {}
