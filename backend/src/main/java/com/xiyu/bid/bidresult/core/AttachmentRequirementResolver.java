package com.xiyu.bid.bidresult.core;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;

public final class AttachmentRequirementResolver {

    private AttachmentRequirementResolver() {
    }

    public static BidResultAttachmentRef.AttachmentType requiredFor(AwardRegistration.ResultOutcome result) {
        return result == AwardRegistration.ResultOutcome.WON
                ? BidResultAttachmentRef.AttachmentType.NOTICE
                : BidResultAttachmentRef.AttachmentType.REPORT;
    }

    public static BidResultAttachmentRef.AttachmentType requiredFor(BidResultFetchResult.Result result) {
        return result == BidResultFetchResult.Result.WON
                ? BidResultAttachmentRef.AttachmentType.NOTICE
                : BidResultAttachmentRef.AttachmentType.REPORT;
    }
}

