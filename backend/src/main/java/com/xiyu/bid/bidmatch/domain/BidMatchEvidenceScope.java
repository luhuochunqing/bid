package com.xiyu.bid.bidmatch.domain;

public record BidMatchEvidenceScope(
        String caseIndustryCode,
        String keyword,
        String purchaserName,
        String region
) {

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
