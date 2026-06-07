package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.tenderupload.entity.TenderFile;
import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderTaskStateMachineTest {

    private TenderTaskStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        TenderProcessingProperties properties = new TenderProcessingProperties();
        properties.setMaxRetries(3);
        properties.setRetryDelaysMinutes(List.of(1, 5, 15));
        stateMachine = new TenderTaskStateMachine(properties);
    }

    @Test
    void markRunning_shouldSetExecutionMetadata() {
        TenderTask task = task(0, TenderTaskStatus.QUEUED);

        stateMachine.markRunning(task, "worker-a", LocalDateTime.of(2026, 4, 22, 10, 0));

        assertEquals(TenderTaskStatus.RUNNING, task.getStatus());
        assertEquals("worker-a", task.getLockedBy());
        assertNotNull(task.getStartedAt());
        assertNull(task.getErrorCode());
    }

    @Test
    void markRetryOrDlq_shouldMoveToRetryBeforeMaxAttempts() {
        TenderTask task = task(1, TenderTaskStatus.RUNNING);

        TenderTaskStateMachine.RetryDecision decision = stateMachine.markRetryOrDlq(task, "ERR", "temporary");

        assertFalse(decision.movedToDlq());
        assertEquals(5, decision.delayMinutes());
        assertEquals(TenderTaskStatus.RETRYING, task.getStatus());
        assertEquals(2, task.getAttempts());
        assertNotNull(task.getAvailableAt());
    }

    @Test
    void markRetryOrDlq_shouldMoveToDlqAtMaxAttempts() {
        TenderTask task = task(2, TenderTaskStatus.RUNNING);

        TenderTaskStateMachine.RetryDecision decision = stateMachine.markRetryOrDlq(task, "ERR", "fatal");

        assertTrue(decision.movedToDlq());
        assertEquals(TenderTaskStatus.DLQ, task.getStatus());
        assertEquals(3, task.getAttempts());
        assertNotNull(task.getFinishedAt());
    }

    private TenderTask task(int attempts, TenderTaskStatus status) {
        TenderFile file = TenderFile.builder().id(10L).userId(1L).filePath("2026/04/test.pdf").build();
        return TenderTask.builder()
                .id(20L)
                .file(file)
                .status(status)
                .attempts(attempts)
                .priority(5)
                .availableAt(LocalDateTime.now())
                .build();
    }
}
