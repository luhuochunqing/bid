// Input: aggregated subscription data for display
// Output: read-only projection returned to clients
// Pos: DTO/订阅数据传输对象
package com.xiyu.bid.tenderkeyword.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SubscriptionDTO(
    Long id,
    String name,
    String logicOperator,
    String status,
    List<String> keywords,
    Integer matchCount,
    LocalDateTime lastMatchedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
