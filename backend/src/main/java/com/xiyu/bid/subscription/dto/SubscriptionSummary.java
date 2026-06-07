// Input: Subscription entity
// Output: read-only projection returned to clients
// Pos: DTO/订阅摘要
package com.xiyu.bid.subscription.dto;

import java.time.LocalDateTime;

public record SubscriptionSummary(
    Long id,
    String targetEntityType,
    Long targetEntityId,
    LocalDateTime createdAt
) {
}
