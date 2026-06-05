// Input: TenderKeywordMatchLog entity
// Output: read-only projection for match results
// Pos: DTO/匹配结果数据
package com.xiyu.bid.tenderkeyword.dto;

import java.time.LocalDateTime;

public record MatchResultDTO(
    Long id,
    Long subscriptionId,
    String subscriptionName,
    Long tenderId,
    String tenderTitle,
    String matchedKeywords,
    Boolean notified,
    LocalDateTime createdAt
) {
}
