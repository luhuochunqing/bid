package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Bid domain metrics.
 * Records bid submission and lifecycle events.
 */
@Component
public class BidMetrics extends BaseMetrics {

    private final Counter bidSubmittedSuccessCounter;
    private final Counter bidSubmittedFailureCounter;
    private final Counter bidUpdatedCounter;
    private final Counter bidWithdrawnCounter;
    private final Timer bidSubmitTimer;

    public BidMetrics(MeterRegistry registry) {
        super(registry);

        this.bidSubmittedSuccessCounter = Counter.builder(PREFIX + "_bid_submitted_total")
                .description("Total bids submitted successfully")
                .tag("status", "success")
                .register(registry);

        this.bidSubmittedFailureCounter = Counter.builder(PREFIX + "_bid_submitted_total")
                .description("Total bid submission failures")
                .tag("status", "failure")
                .register(registry);

        this.bidUpdatedCounter = Counter.builder(PREFIX + "_bid_updated_total")
                .description("Total bid updates")
                .register(registry);

        this.bidWithdrawnCounter = Counter.builder(PREFIX + "_bid_withdrawn_total")
                .description("Total bids withdrawn")
                .register(registry);

        this.bidSubmitTimer = Timer.builder(PREFIX + "_bid_submit_duration_seconds")
                .description("Bid submission duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordBidSubmitted() {
        bidSubmittedSuccessCounter.increment();
    }

    public void recordBidSubmissionFailed() {
        bidSubmittedFailureCounter.increment();
    }

    public void recordBidUpdated() {
        bidUpdatedCounter.increment();
    }

    public void recordBidWithdrawn() {
        bidWithdrawnCounter.increment();
    }

    public void recordBidSubmitLatency(Runnable operation) {
        bidSubmitTimer.record(operation);
    }

    public <T> T recordBidSubmitLatency(Supplier<T> operation) {
        return bidSubmitTimer.record(operation);
    }
}
