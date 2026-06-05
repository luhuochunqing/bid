package com.xiyu.bid.tenderupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.tender-processing")
public class TenderProcessingProperties {

    private String storageRoot = "/data/shared/tenders";

    private int maxGlobalConcurrency = 2;

    private int maxPerUserConcurrency = 1;

    private int maxRetries = 3;

    private List<Integer> retryDelaysMinutes = List.of(1, 5, 15);

    private int estimatedTaskSeconds = 120;

    private double cpuThreshold = 0.92d;

    private double memoryThreshold = 0.92d;

    private long workerFixedDelayMs = 5_000L;

    public Path storageRootPath() {
        return Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public int retryDelayMinutesForAttempt(int attempt) {
        if (attempt <= 0) {
            return retryDelaysMinutes.getFirst();
        }
        if (attempt > retryDelaysMinutes.size()) {
            return retryDelaysMinutes.getLast();
        }
        return retryDelaysMinutes.get(attempt - 1);
    }
}
