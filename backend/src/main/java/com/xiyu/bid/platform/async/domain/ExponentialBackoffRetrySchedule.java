package com.xiyu.bid.platform.async.domain;

public final class ExponentialBackoffRetrySchedule implements AsyncRetrySchedule {
    private final int baseDelaySeconds;
    private final int maxDelaySeconds;
    private final int maxExponent;

    public ExponentialBackoffRetrySchedule(int baseDelaySeconds, int maxDelaySeconds, int maxExponent) {
        if (baseDelaySeconds <= 0) {
            throw new IllegalArgumentException("baseDelaySeconds must be positive");
        }
        if (maxDelaySeconds < baseDelaySeconds) {
            throw new IllegalArgumentException("maxDelaySeconds must be >= baseDelaySeconds");
        }
        if (maxExponent < 0) {
            throw new IllegalArgumentException("maxExponent must be >= 0");
        }
        this.baseDelaySeconds = baseDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
        this.maxExponent = maxExponent;
    }

    @Override
    public int nextDelaySeconds(int attempt) {
        int normalizedAttempt = Math.max(attempt, 0);
        int exponent = Math.min(normalizedAttempt, maxExponent);
        int multiplier = 1 << exponent;
        return Math.min(baseDelaySeconds * multiplier, maxDelaySeconds);
    }
}
