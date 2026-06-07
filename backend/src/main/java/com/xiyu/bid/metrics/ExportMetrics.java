package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Export domain metrics.
 * Records export operation success, failure, and size limit events.
 */
@Component
public class ExportMetrics extends BaseMetrics {

    private final Counter exportSuccessCounter;
    private final Counter exportFailureCounter;
    private final Counter exportSizeExceededCounter;

    public ExportMetrics(MeterRegistry registry) {
        super(registry);

        this.exportSuccessCounter = Counter.builder(PREFIX + "_export_total")
                .description("Total successful exports")
                .tag("status", "success")
                .register(registry);

        this.exportFailureCounter = Counter.builder(PREFIX + "_export_total")
                .description("Total export failures")
                .tag("status", "failure")
                .register(registry);

        this.exportSizeExceededCounter = Counter.builder(PREFIX + "_export_total")
                .description("Total exports rejected due to size limit")
                .tag("status", "size_exceeded")
                .register(registry);
    }

    public void recordExportSuccess() {
        exportSuccessCounter.increment();
    }

    public void recordExportFailure() {
        exportFailureCounter.increment();
    }

    public void recordExportSizeExceeded() {
        exportSizeExceededCounter.increment();
    }
}
