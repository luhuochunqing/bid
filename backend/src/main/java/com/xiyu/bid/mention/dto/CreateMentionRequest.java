package com.xiyu.bid.mention.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateMentionRequest(
    @NotBlank @Size(max = 10000) String content,
    @Size(max = 50) String sourceEntityType,
    @Positive Long sourceEntityId,
    @Size(max = 200) String title
) {
}
