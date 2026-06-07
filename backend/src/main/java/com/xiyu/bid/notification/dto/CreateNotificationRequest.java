package com.xiyu.bid.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record CreateNotificationRequest(
    @NotBlank String type,
    String sourceEntityType,
    Long sourceEntityId,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 10000) String body,
    Map<String, Object> payload,
    @NotEmpty @Size(max = 1000) List<Long> recipientUserIds
) {
}
