package com.xiyu.bid.scoreparse.application;

import java.util.Optional;

/** 立项/底稿取正文结果：有源则 source 有值，无源则 emptyReason 为 400/FAILED 文案。 */
public record TenderIntake(Optional<TenderTextSource> source, String emptyReason) {

    public static TenderIntake found(TenderTextSource source) {
        return new TenderIntake(Optional.of(source), null);
    }

    public static TenderIntake unavailable(String reason) {
        return new TenderIntake(Optional.empty(), reason);
    }
}
