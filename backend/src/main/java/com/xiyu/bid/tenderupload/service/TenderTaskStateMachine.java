// Input: 当前任务状态、错误信息与重试策略
// Output: 标准化状态流转（RUNNING/SUCCEEDED/RETRYING/DLQ）
// Pos: TenderUpload/Service
// 维护声明: 所有任务状态变化集中在此，避免分散写状态导致语义漂移.
package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenderTaskStateMachine {

    private final TenderProcessingProperties properties;

    public void markRunning(TenderTask task, String workerId, LocalDateTime now) {
        task.setStatus(TenderTaskStatus.RUNNING);
        task.setLockedBy(workerId);
        task.setLockedAt(now);
        task.setStartedAt(now);
        task.setErrorCode(null);
        task.setErrorMessage(null);
    }

    public void markSucceeded(TenderTask task) {
        task.setStatus(TenderTaskStatus.SUCCEEDED);
        task.setFinishedAt(LocalDateTime.now());
        task.setLockedAt(null);
        task.setLockedBy(null);
        task.setAvailableAt(LocalDateTime.now());
        task.setErrorCode(null);
        task.setErrorMessage(null);
    }

    public RetryDecision markRetryOrDlq(TenderTask task, String errorCode, String errorMessage) {
        int nextAttempt = (task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
        task.setAttempts(nextAttempt);
        task.setErrorCode(errorCode);
        task.setErrorMessage(trimError(errorMessage));
        task.setLockedAt(null);
        task.setLockedBy(null);

        if (nextAttempt >= properties.getMaxRetries()) {
            task.setStatus(TenderTaskStatus.DLQ);
            task.setFinishedAt(LocalDateTime.now());
            return RetryDecision.dlq();
        }

        int delayMinutes = properties.retryDelayMinutesForAttempt(nextAttempt);
        task.setStatus(TenderTaskStatus.RETRYING);
        task.setAvailableAt(LocalDateTime.now().plusMinutes(delayMinutes));
        task.setFinishedAt(null);
        return RetryDecision.retry(delayMinutes);
    }

    private String trimError(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown processing error";
        }
        return error.length() > 900 ? error.substring(0, 900) : error;
    }

    public record RetryDecision(boolean movedToDlq, int delayMinutes) {
        static RetryDecision dlq() {
            return new RetryDecision(true, 0);
        }

        static RetryDecision retry(int delayMinutes) {
            return new RetryDecision(false, delayMinutes);
        }
    }
}
