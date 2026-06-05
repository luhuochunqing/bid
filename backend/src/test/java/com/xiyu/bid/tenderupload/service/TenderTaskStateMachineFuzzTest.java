package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.tenderupload.entity.TenderFile;
import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-4 fuzz specialization for the stateful {@link TenderTaskStateMachine}.
 * Drives random attempt counts, error-message shapes and transition sequences
 * through the three mutators to verify the invariants that hold over every
 * reachable state.
 */
class TenderTaskStateMachineFuzzTest {

    private static final long FUZZ_SEED = 0xF4_7A5C_DAD_0L;
    private static final int FUZZ_ITERATIONS = 2_000;
    private static final int MAX_ERROR_CHARS_IN_DB = 900;
    private static final int MAX_RETRIES = 3;

    private TenderTaskStateMachine stateMachine;

    private TenderTaskStateMachine buildStateMachine() {
        TenderProcessingProperties props = new TenderProcessingProperties();
        props.setMaxRetries(MAX_RETRIES);
        props.setRetryDelaysMinutes(List.of(1, 5, 15));
        return new TenderTaskStateMachine(props);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        stateMachine = buildStateMachine();
    }

    @ParameterizedTest
    @EnumSource(TenderTaskStatus.class)
    void markRunningAlwaysSetsRunningMetadata(TenderTaskStatus initialStatus) {
        TenderTask task = task(initialStatus, 0, "prev-err", "prev message");
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 10, 0);

        stateMachine.markRunning(task, "worker-x", now);

        assertThat(task.getStatus()).isEqualTo(TenderTaskStatus.RUNNING);
        assertThat(task.getLockedBy()).isEqualTo("worker-x");
        assertThat(task.getLockedAt()).isEqualTo(now);
        assertThat(task.getStartedAt()).isEqualTo(now);
        assertThat(task.getErrorCode()).isNull();
        assertThat(task.getErrorMessage()).isNull();
    }

    @ParameterizedTest
    @EnumSource(TenderTaskStatus.class)
    void markSucceededClearsErrorAndLockMetadata(TenderTaskStatus initialStatus) {
        TenderTask task = task(initialStatus, 2, "ERR_X", "transient failure");
        task.setLockedBy("worker-y");
        task.setLockedAt(LocalDateTime.now());

        stateMachine.markSucceeded(task);

        assertThat(task.getStatus()).isEqualTo(TenderTaskStatus.SUCCEEDED);
        assertThat(task.getFinishedAt()).isNotNull();
        assertThat(task.getAvailableAt()).isNotNull();
        assertThat(task.getLockedBy()).isNull();
        assertThat(task.getLockedAt()).isNull();
        assertThat(task.getErrorCode()).isNull();
        assertThat(task.getErrorMessage()).isNull();
    }

    @Test
    void markRetryOrDlqFuzzPreservesInvariants() {
        Random rng = new Random(FUZZ_SEED);

        for (int i = 0; i < FUZZ_ITERATIONS; i++) {
            int initialAttempts = rng.nextInt(MAX_RETRIES + 3); // include over-cap values
            String errorCode = randomErrorCode(rng);
            String errorMessage = randomErrorMessage(rng);

            TenderTask task = task(TenderTaskStatus.RUNNING, initialAttempts, null, null);
            task.setLockedBy("worker-" + i);
            task.setLockedAt(LocalDateTime.now());

            TenderTaskStateMachine.RetryDecision decision = stateMachine.markRetryOrDlq(task, errorCode, errorMessage);

            // 1) attempts ALWAYS grows by exactly 1, never regresses.
            assertThat(task.getAttempts()).isEqualTo(initialAttempts + 1);

            // 2) lock metadata is always cleared after a failure routing.
            assertThat(task.getLockedBy()).isNull();
            assertThat(task.getLockedAt()).isNull();

            // 3) error message is always non-null, non-blank, and ≤ 900 chars.
            assertThat(task.getErrorMessage())
                    .as("error message must be non-blank and bounded at column width")
                    .isNotNull()
                    .isNotBlank();
            assertThat(task.getErrorMessage().length()).isLessThanOrEqualTo(MAX_ERROR_CHARS_IN_DB);

            // 4) DLQ iff new attempts reach max retries, else RETRYING with future availability.
            if (task.getAttempts() >= MAX_RETRIES) {
                assertThat(decision.movedToDlq()).isTrue();
                assertThat(task.getStatus()).isEqualTo(TenderTaskStatus.DLQ);
                assertThat(task.getFinishedAt()).isNotNull();
            } else {
                assertThat(decision.movedToDlq()).isFalse();
                assertThat(decision.delayMinutes()).isPositive();
                assertThat(task.getStatus()).isEqualTo(TenderTaskStatus.RETRYING);
                assertThat(task.getAvailableAt()).isNotNull();
                assertThat(task.getFinishedAt()).isNull();
            }
        }
    }

    @Test
    void markRetryOrDlqFallbackMessageForNullOrBlank() {
        for (String blank : new String[]{null, "", "   ", "\n\t"}) {
            TenderTask task = task(TenderTaskStatus.RUNNING, 0, null, null);
            stateMachine.markRetryOrDlq(task, "ERR", blank);
            assertThat(task.getErrorMessage())
                    .as("null or blank error message must fall back to a readable placeholder")
                    .isEqualTo("Unknown processing error");
        }
    }

    @Test
    void markRetryOrDlqTerminatesInDlqWithinMaxRetriesPlusOne() {
        // Property: starting from attempts=0, at most `maxRetries` failure routings
        // are needed before the task lands in DLQ — the state machine never loops
        // indefinitely in RETRYING.
        TenderTask task = task(TenderTaskStatus.RUNNING, 0, null, null);

        int safetyBound = MAX_RETRIES + 2;
        int loops = 0;
        while (task.getStatus() != TenderTaskStatus.DLQ && loops < safetyBound) {
            stateMachine.markRetryOrDlq(task, "ERR", "boom");
            loops++;
        }

        assertThat(task.getStatus()).isEqualTo(TenderTaskStatus.DLQ);
        assertThat(loops).isLessThanOrEqualTo(MAX_RETRIES);
    }

    @Test
    void randomLifecycleAlwaysConvergesToTerminalStatus() {
        Random rng = new Random(FUZZ_SEED ^ 0xABCD_1234L);

        for (int runId = 0; runId < 200; runId++) {
            TenderTask task = task(TenderTaskStatus.QUEUED, 0, null, null);
            int safety = 0;

            while (task.getStatus() != TenderTaskStatus.SUCCEEDED
                    && task.getStatus() != TenderTaskStatus.DLQ
                    && safety++ < 50) {
                stateMachine.markRunning(task, "worker-" + runId, LocalDateTime.now());
                if (rng.nextBoolean()) {
                    stateMachine.markSucceeded(task);
                } else {
                    stateMachine.markRetryOrDlq(task, "ERR", "iter-" + safety);
                }
            }

            // Property: every random lifecycle ends in a terminal, non-null status.
            assertThat(task.getStatus())
                    .isIn(TenderTaskStatus.SUCCEEDED, TenderTaskStatus.DLQ);
        }
    }

    private static String randomErrorCode(Random rng) {
        int choice = rng.nextInt(4);
        return switch (choice) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "ERR_A";
            default -> "VERY_LONG_CODE_" + rng.nextInt();
        };
    }

    private static String randomErrorMessage(Random rng) {
        int choice = rng.nextInt(6);
        return switch (choice) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "   ";
            case 3 -> "short failure";
            case 4 -> "x".repeat(899); // boundary just under the limit
            default -> "y".repeat(rng.nextInt(3_000) + 901); // guaranteed over the limit
        };
    }

    private static TenderTask task(TenderTaskStatus status, int attempts, String errorCode, String errorMessage) {
        TenderFile file = TenderFile.builder().id(10L).userId(1L).filePath("2026/05/test.pdf").build();
        return TenderTask.builder()
                .id(20L)
                .file(file)
                .status(status)
                .attempts(attempts)
                .priority(5)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .availableAt(LocalDateTime.now())
                .build();
    }
}
