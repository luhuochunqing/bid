package com.xiyu.bid.ai.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AiJobLifecyclePolicyTest {

    @Test
    void pending_shouldCreatePendingLifecycle() {
        AiJobLifecyclePolicy.JobLifecycle lifecycle = AiJobLifecyclePolicy.pending();

        assertThat(lifecycle.status()).isEqualTo(AiJobLifecyclePolicy.JobStatus.PENDING);
        assertThat(lifecycle.completedAt()).isNull();
        assertThat(lifecycle.errorMessage()).isNull();
    }

    @Test
    void completed_shouldCarryCompletionTime() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 4, 22, 9, 30);

        AiJobLifecyclePolicy.JobLifecycle lifecycle = AiJobLifecyclePolicy.completed(completedAt);

        assertThat(lifecycle.status()).isEqualTo(AiJobLifecyclePolicy.JobStatus.COMPLETED);
        assertThat(lifecycle.completedAt()).isEqualTo(completedAt);
        assertThat(lifecycle.errorMessage()).isNull();
    }

    @Test
    void failed_shouldTrimErrorMessage() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 4, 22, 9, 30);

        AiJobLifecyclePolicy.JobLifecycle lifecycle = AiJobLifecyclePolicy.failed("  provider unavailable  ", completedAt);

        assertThat(lifecycle.status()).isEqualTo(AiJobLifecyclePolicy.JobStatus.FAILED);
        assertThat(lifecycle.completedAt()).isEqualTo(completedAt);
        assertThat(lifecycle.errorMessage()).isEqualTo("provider unavailable");
    }
}
