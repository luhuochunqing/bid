package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.MatchEvidence;

import java.util.Map;

public record BidMatchEvidenceBundle(
        MatchEvidence evidence,
        Map<String, Object> snapshot,
        String fingerprint
) {
}
