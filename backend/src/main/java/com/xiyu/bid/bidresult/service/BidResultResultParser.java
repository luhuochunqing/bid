package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.AwardRegistration;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.exception.BusinessException;

import java.util.Locale;

final class BidResultResultParser {

    AwardRegistration.ResultOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "won" -> AwardRegistration.ResultOutcome.WON;
            case "lost" -> AwardRegistration.ResultOutcome.LOST;
            default -> throw new BusinessException("未知投标结果: " + raw);
        };
    }

    BidResultFetchResult.Result toEntityResult(AwardRegistration.ResultOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        return outcome == AwardRegistration.ResultOutcome.WON
                ? BidResultFetchResult.Result.WON
                : BidResultFetchResult.Result.LOST;
    }
}

