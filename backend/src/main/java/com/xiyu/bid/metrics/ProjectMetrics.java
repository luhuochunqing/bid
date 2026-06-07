package com.xiyu.bid.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Project domain metrics.
 * Records project creation, update, deletion, and export events.
 */
@Component
public class ProjectMetrics extends BaseMetrics {

    private final Counter projectCreatedSuccessCounter;
    private final Counter projectCreatedFailureCounter;
    private final Counter projectUpdatedCounter;
    private final Counter projectDeletedCounter;
    private final Counter projectExportedCounter;
    private final Timer projectCreateTimer;

    public ProjectMetrics(MeterRegistry registry) {
        super(registry);

        this.projectCreatedSuccessCounter = Counter.builder(PREFIX + "_project_created_total")
                .description("Total projects created")
                .tag("status", "success")
                .register(registry);

        this.projectCreatedFailureCounter = Counter.builder(PREFIX + "_project_created_total")
                .description("Total project creation failures")
                .tag("status", "failure")
                .register(registry);

        this.projectUpdatedCounter = Counter.builder(PREFIX + "_project_updated_total")
                .description("Total project updates")
                .register(registry);

        this.projectDeletedCounter = Counter.builder(PREFIX + "_project_deleted_total")
                .description("Total projects deleted")
                .register(registry);

        this.projectExportedCounter = Counter.builder(PREFIX + "_project_exported_total")
                .description("Total project exports")
                .register(registry);

        this.projectCreateTimer = Timer.builder(PREFIX + "_project_create_duration_seconds")
                .description("Project creation duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordProjectCreated() {
        projectCreatedSuccessCounter.increment();
    }

    public void recordProjectCreationFailed() {
        projectCreatedFailureCounter.increment();
    }

    public void recordProjectUpdated() {
        projectUpdatedCounter.increment();
    }

    public void recordProjectDeleted() {
        projectDeletedCounter.increment();
    }

    public void recordProjectExported() {
        projectExportedCounter.increment();
    }

    public void recordProjectCreateLatency(Runnable operation) {
        projectCreateTimer.record(operation);
    }

    public <T> T recordProjectCreateLatency(Supplier<T> operation) {
        return projectCreateTimer.record(operation);
    }
}
