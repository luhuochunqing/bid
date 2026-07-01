package com.xiyu.bid.mention.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateMentionRequest(
    @NotBlank @Size(max = 10000) String content,
    @Size(max = 50) String sourceEntityType,
    @Positive Long sourceEntityId,
    @Size(max = 200) String title,
    Map<String, Object> payload
) {
    // 兼容旧调用方（无 payload）的紧凑构造函数
    public CreateMentionRequest(String content, String sourceEntityType, Long sourceEntityId, String title) {
        this(content, sourceEntityType, sourceEntityId, title, null);
    }
}
