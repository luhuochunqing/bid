package com.xiyu.bid.bidresult.core;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;

import java.util.ArrayList;
import java.util.List;

public final class FetchResultConfirmationPolicy {

    private FetchResultConfirmationPolicy() {
    }

    public static Decision validateForConfirmation(BidResultFetchResult fetchResult, AwardRegistration registration) {
        List<String> errors = new ArrayList<>();
        if (fetchResult == null) {
            errors.add("待确认记录不能为空");
        }
        if (fetchResult != null && fetchResult.getStatus() == BidResultFetchResult.Status.IGNORED) {
            errors.add("已忽略的记录不能确认");
        }
        var validation = AwardRegistrationValidation.validate(registration);
        if (!validation.valid()) {
            errors.addAll(validation.errors());
        }
        return errors.isEmpty() ? Decision.success() : Decision.failure(errors);
    }

    public static Decision validateIgnore(String comment) {
        if (comment == null || comment.isBlank()) {
            return Decision.failure(List.of("忽略原因不能为空"));
        }
        return Decision.success();
    }

    public record Decision(boolean valid, List<String> errors) {
        public Decision {
            errors = List.copyOf(errors);
        }

        public static Decision success() {
            return new Decision(true, List.of());
        }

        public static Decision failure(List<String> errors) {
            return new Decision(false, errors);
        }
    }
}
