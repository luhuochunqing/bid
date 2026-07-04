package com.xiyu.bid.casework.application.service;

/**
 * Rate limiter used to throttle embedding calls.
 */
@FunctionalInterface
public interface EmbeddingRateLimiter {

    /**
     * Blocks until the next embedding request is allowed.
     */
    void acquire();
}
