package com.xiyu.bid.platform.async.domain;

public interface AsyncRetrySchedule {
    int nextDelaySeconds(int attempt);
}
