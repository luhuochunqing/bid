package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * API domain metrics.
 * Records API-level events like rate limiting, not found, and unauthorized access.
 */
@Component
public class ApiMetrics extends BaseMetrics {

    private final Counter apiRateLimitedCounter;
    private final Counter apiNotFoundCounter;
    private final Counter apiUnauthorizedCounter;

    public ApiMetrics(MeterRegistry registry) {
        super(registry);

        this.apiRateLimitedCounter = Counter.builder(PREFIX + "_api_rate_limited_total")
                .description("Total requests rejected by rate limiting")
                .register(registry);

        this.apiNotFoundCounter = Counter.builder(PREFIX + "_api_not_found_total")
                .description("Total requests to non-existent endpoints")
                .register(registry);

        this.apiUnauthorizedCounter = Counter.builder(PREFIX + "_api_unauthorized_total")
                .description("Total unauthorized access attempts")
                .register(registry);
    }

    public void recordRateLimited() {
        apiRateLimitedCounter.increment();
    }

    public void recordNotFound() {
        apiNotFoundCounter.increment();
    }

    public void recordUnauthorized() {
        apiUnauthorizedCounter.increment();
    }
}
