package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Cache domain metrics.
 * Records cache hit and miss events.
 */
@Component
public class CacheMetrics extends BaseMetrics {

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public CacheMetrics(MeterRegistry registry) {
        super(registry);

        this.cacheHitCounter = Counter.builder(PREFIX + "_cache_hit_total")
                .description("Total cache hits")
                .register(registry);

        this.cacheMissCounter = Counter.builder(PREFIX + "_cache_miss_total")
                .description("Total cache misses")
                .register(registry);
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }
}
