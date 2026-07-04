package com.xiyu.bid.casework.application.service;

import org.springframework.stereotype.Component;

/**
 * Enforces a fixed minimum interval between embedding requests.
 *
 * <p>Example: an interval of 100 ms yields at most 10 requests per second.</p>
 */
@Component
public class FixedIntervalRateLimiter implements EmbeddingRateLimiter {

    private final long intervalMillis;
    private long lastAcquireMillis;

    public FixedIntervalRateLimiter() {
        this(100L);
    }

    public FixedIntervalRateLimiter(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    @Override
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long wait = intervalMillis - (now - lastAcquireMillis);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Rate limiter interrupted", e);
            }
        }
        lastAcquireMillis = System.currentTimeMillis();
    }
}
