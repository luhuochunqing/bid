package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Organization sync domain metrics.
 * Records organization data synchronization success and failure events.
 */
@Component
public class OrgSyncMetrics extends BaseMetrics {

    private final Counter orgSyncSuccessCounter;
    private final Counter orgSyncFailureCounter;
    private final Timer orgSyncTimer;

    public OrgSyncMetrics(MeterRegistry registry) {
        super(registry);

        this.orgSyncSuccessCounter = Counter.builder(PREFIX + "_org_sync_total")
                .description("Total successful organization syncs")
                .tag("status", "success")
                .register(registry);

        this.orgSyncFailureCounter = Counter.builder(PREFIX + "_org_sync_total")
                .description("Total organization sync failures")
                .tag("status", "failure")
                .register(registry);

        this.orgSyncTimer = Timer.builder(PREFIX + "_org_sync_duration_seconds")
                .description("Organization sync duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordOrgSyncSuccess() {
        orgSyncSuccessCounter.increment();
    }

    public void recordOrgSyncFailure() {
        orgSyncFailureCounter.increment();
    }

    public void recordOrgSyncTime(long durationMillis) {
        orgSyncTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public <T> T recordOrgSync(Supplier<T> operation) {
        return orgSyncTimer.record(operation);
    }
}
