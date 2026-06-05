package com.xiyu.bid.ai.core;

import java.time.LocalDateTime;

public final class AiJobLifecyclePolicy {

    private AiJobLifecyclePolicy() {
    }

    public static JobLifecycle pending() {
        return new JobLifecycle(JobStatus.PENDING, null, null);
    }

    public static JobLifecycle completed(LocalDateTime completedAt) {
        return new JobLifecycle(JobStatus.COMPLETED, completedAt, null);
    }

    public static JobLifecycle failed(String errorMessage, LocalDateTime completedAt) {
        return new JobLifecycle(JobStatus.FAILED, completedAt, errorMessage == null ? null : errorMessage.trim());
    }

    public record JobLifecycle(JobStatus status, LocalDateTime completedAt, String errorMessage) {
    }

    public enum JobStatus {
        PENDING,
        COMPLETED,
        FAILED
    }
}
