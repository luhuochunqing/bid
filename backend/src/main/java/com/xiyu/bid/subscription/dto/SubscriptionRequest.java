// Input: subscribe/unsubscribe REST payloads
// Output: validated record with targetEntityType + targetEntityId
// Pos: DTO/订阅请求
package com.xiyu.bid.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubscriptionRequest(
    @NotBlank String targetEntityType,
    @NotNull @Positive Long targetEntityId
) {
}
