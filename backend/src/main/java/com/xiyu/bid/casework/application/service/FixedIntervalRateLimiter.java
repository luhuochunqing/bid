package com.xiyu.bid.casework.application.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Enforces a fixed minimum interval between embedding requests.
 *
 * <p>Example: an interval of 100 ms yields at most 10 requests per second.</p>
 *
 * <p>使用 {@link System#nanoTime()} 测量时间间隔，避免系统时钟回拨导致的偏差。</p>
 */
@Component
public class FixedIntervalRateLimiter implements EmbeddingRateLimiter {

    private final long intervalNanos;
    private long lastAcquireNanos;

    public FixedIntervalRateLimiter() {
        this(100L);
    }

    public FixedIntervalRateLimiter(long intervalMillis) {
        this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
    }

    @Override
    public synchronized void acquire() {
        long now = System.nanoTime();
        long waitNanos = intervalNanos - (now - lastAcquireNanos);
        if (waitNanos > 0) {
            try {
                long waitMillis = TimeUnit.NANOSECONDS.toMillis(waitNanos);
                int waitNanosRemainder = (int) (waitNanos % 1_000_000);
                Thread.sleep(waitMillis, waitNanosRemainder);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Rate limiter interrupted", e);
            }
        }
        lastAcquireNanos = System.nanoTime();
    }
}
