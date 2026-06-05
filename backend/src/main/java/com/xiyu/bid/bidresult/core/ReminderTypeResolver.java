package com.xiyu.bid.bidresult.core;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;

public final class ReminderTypeResolver {

    private ReminderTypeResolver() {
    }

    public static BidResultReminder.ReminderType resolve(BidResultFetchResult.Result result) {
        return result == BidResultFetchResult.Result.WON
                ? BidResultReminder.ReminderType.NOTICE
                : BidResultReminder.ReminderType.REPORT;
    }
}

