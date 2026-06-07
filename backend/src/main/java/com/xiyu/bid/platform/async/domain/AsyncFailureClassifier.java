package com.xiyu.bid.platform.async.domain;

public interface AsyncFailureClassifier {
    AsyncFailureKind classify(Throwable error);
}
