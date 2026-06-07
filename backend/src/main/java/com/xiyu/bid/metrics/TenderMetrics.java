package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tender processing domain metrics.
 * Records tender processing success and failure events.
 */
@Component
public class TenderMetrics extends BaseMetrics {

    private final Counter tenderProcessedSuccessCounter;
    private final Counter tenderProcessedFailureCounter;
    private final Timer tenderProcessingTimer;

    public TenderMetrics(MeterRegistry registry) {
        super(registry);

        this.tenderProcessedSuccessCounter = Counter.builder(PREFIX + "_tender_processed_total")
                .description("Total tenders processed successfully")
                .tag("status", "success")
                .register(registry);

        this.tenderProcessedFailureCounter = Counter.builder(PREFIX + "_tender_processed_total")
                .description("Total tender processing failures")
                .tag("status", "failure")
                .register(registry);

        this.tenderProcessingTimer = Timer.builder(PREFIX + "_tender_processing_duration_seconds")
                .description("Tender processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordTenderProcessed() {
        tenderProcessedSuccessCounter.increment();
    }

    public void recordTenderProcessingFailed() {
        tenderProcessedFailureCounter.increment();
    }

    public void recordTenderProcessingTime(long durationMillis) {
        tenderProcessingTimer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    public <T> T recordTenderProcessing(Supplier<T> operation) {
        return tenderProcessingTimer.record(operation);
    }
}
