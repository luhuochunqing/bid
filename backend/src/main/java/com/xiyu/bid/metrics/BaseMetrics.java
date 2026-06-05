package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Base class for domain-specific metrics holders.
 * Provides shared constants and registry access.
 */
public abstract class BaseMetrics {

    protected static final String PREFIX = "xiyu_bid";

    protected final MeterRegistry registry;

    protected BaseMetrics(MeterRegistry registry) {
        this.registry = registry;
    }
}
