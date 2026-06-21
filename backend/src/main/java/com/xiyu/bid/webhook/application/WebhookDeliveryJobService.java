package com.xiyu.bid.webhook.application;

import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;
import com.xiyu.bid.platform.async.domain.ExponentialBackoffRetrySchedule;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryDlq;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryDlqRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryLog;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryLogRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTask;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskRepository;
import com.xiyu.bid.webhook.infrastructure.WebhookDeliveryTaskStatus;
import com.xiyu.bid.webhook.infrastructure.WebhookHttpSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryJobService {
    private static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 20;

    private final WebhookDeliveryTaskRepository taskRepository;
    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookDeliveryDlqRepository dlqRepository;
    private final WebhookHttpSender httpSender;
    private final WebhookFailureClassifier failureClassifier;
    private final AsyncDecisionResolver decisionResolver;
    private final AsyncObservabilityRecorder observabilityRecorder;
    /** 重试间隔: 1min / 5min / 15min（与 CRM 结果确认回调保持一致） */
    private final ExponentialBackoffRetrySchedule retrySchedule = new ExponentialBackoffRetrySchedule(60, 900, 5);

    @Scheduled(fixedDelayString = "${webhook.crm.worker-fixed-delay-ms:5000}")
    public void processDueTasks() {
        List<WebhookDeliveryTask> tasks = taskRepository.findRunnableTasks(LocalDateTime.now(), BATCH_SIZE);
        tasks.forEach(this::processTaskSafely);
    }

    @Transactional
    public void processTaskSafely(WebhookDeliveryTask task) {
        WebhookDeliveryTask managedTask = taskRepository.findById(task.getId()).orElse(task);
        managedTask.setStatus(WebhookDeliveryTaskStatus.PROCESSING);
        managedTask.setUpdatedAt(LocalDateTime.now());

        try {
            WebhookSendResult result = httpSender.send(managedTask.getTargetUrl(), managedTask.getPayload());
            log.info("Webhook sent: taskId={}, tenderId={}, targetUrl={}, statusCode={}, responseBody={}, payload={}",
                    managedTask.getId(), managedTask.getTenderId(), managedTask.getTargetUrl(),
                    result.statusCode(), result.responseBody(), managedTask.getPayload());
            if (result.successful()) {
                handleSuccess(managedTask, result);
                return;
            }
            handleStatusFailure(managedTask, result);
        } catch (IOException | InterruptedException | RuntimeException ex) {
            log.error("Webhook send threw: taskId={}, tenderId={}, targetUrl={}, payload={}, error={}",
                    managedTask.getId(), managedTask.getTenderId(), managedTask.getTargetUrl(),
                    managedTask.getPayload(), ex.getMessage(), ex);
            handleThrowableFailure(managedTask, ex);
        }
    }

    private void handleSuccess(WebhookDeliveryTask task, WebhookSendResult result) {
        task.setStatus(WebhookDeliveryTaskStatus.DELIVERED);
        task.setUpdatedAt(result.respondedAt());
        task.setLastErrorCode(null);
        task.setLastErrorMessage(null);
        taskRepository.save(task);
        logRepository.save(WebhookDeliveryLog.builder()
                .tenderId(task.getTenderId())
                .targetUrl(task.getTargetUrl())
                .retryCount(task.getAttemptCount())
                .statusCode(result.statusCode())
                .responseBody(result.responseBody())
                .status("DELIVERED")
                .build());
        observabilityRecorder.recordSuccess("webhook", task.getEventType(), task.getBusinessKey());
    }

    private void handleStatusFailure(WebhookDeliveryTask task, WebhookSendResult result) {
        AsyncFailureKind failureKind = failureClassifier.classifyStatusCode(result.statusCode() == null ? 500 : result.statusCode());
        AsyncHandlingDecision decision = decisionResolver.resolve(
                failureKind,
                task.getAttemptCount() + 1,
                MAX_ATTEMPTS,
                retrySchedule,
                true
        );
        applyFailureDecision(task, decision, result.errorMessage(), result.responseBody(), result.statusCode());
    }

    private void handleThrowableFailure(WebhookDeliveryTask task, Exception ex) {
        AsyncFailureKind failureKind = failureClassifier.classify(ex);
        AsyncHandlingDecision decision = decisionResolver.resolve(
                failureKind,
                task.getAttemptCount() + 1,
                MAX_ATTEMPTS,
                retrySchedule,
                true
        );
        applyFailureDecision(task, decision, ex.getMessage(), ex.getMessage(), null);
    }

    private void applyFailureDecision(
            WebhookDeliveryTask task,
            AsyncHandlingDecision decision,
            String errorMessage,
            String responseBody,
            Integer statusCode
    ) {
        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setLastErrorCode(decision.reasonCode());
        task.setLastErrorMessage(trim(errorMessage));
        task.setUpdatedAt(LocalDateTime.now());

        switch (decision.action()) {
            case RETRY -> {
                task.setStatus(WebhookDeliveryTaskStatus.PENDING_RETRY);
                task.setNextRetryAt(LocalDateTime.now().plusSeconds(decision.nextRetryDelaySeconds()));
                taskRepository.save(task);
                observabilityRecorder.recordRetry("webhook", task.getEventType(), task.getBusinessKey(), nextAttempt, decision);
            }
            case DEAD_LETTER -> {
                task.setStatus(WebhookDeliveryTaskStatus.DEAD_LETTER);
                task.setNextRetryAt(null);
                taskRepository.save(task);
                dlqRepository.save(WebhookDeliveryDlq.builder()
                        .taskId(task.getId())
                        .tenderId(task.getTenderId())
                        .businessKey(task.getBusinessKey())
                        .reasonCode(decision.reasonCode())
                        .errorMessage(trim(errorMessage))
                        .payload(task.getPayload())
                        .build());
                observabilityRecorder.recordDeadLetter("webhook", task.getEventType(), task.getBusinessKey(), decision.reasonCode());
            }
            case DROP, SUCCEED_WITH_LOG -> {
                task.setStatus(WebhookDeliveryTaskStatus.DELIVERED);
                task.setNextRetryAt(null);
                taskRepository.save(task);
                observabilityRecorder.recordDrop("webhook", task.getEventType(), task.getBusinessKey(), decision.reasonCode(), decision.alertRequired());
            }
            case FAIL_MAIN_TRANSACTION -> throw new IllegalStateException("Webhook async side effect should not fail main transaction");
        }

        logRepository.save(WebhookDeliveryLog.builder()
                .tenderId(task.getTenderId())
                .targetUrl(task.getTargetUrl())
                .retryCount(nextAttempt)
                .statusCode(statusCode)
                .responseBody(trim(responseBody))
                .status(decision.action().name())
                .build());
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
