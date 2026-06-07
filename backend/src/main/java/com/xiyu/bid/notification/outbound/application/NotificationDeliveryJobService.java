package com.xiyu.bid.notification.outbound.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.core.OutboundChannel;
import com.xiyu.bid.notification.outbound.core.OutboundStatus;
import com.xiyu.bid.notification.outbound.core.SkipReason;
import com.xiyu.bid.notification.outbound.entity.OutboundLog;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryDlq;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryDlqRepository;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTask;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskRepository;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskStatus;
import com.xiyu.bid.notification.outbound.repository.OutboundLogRepository;
import com.xiyu.bid.notification.outbound.service.NotificationDeliveryResult;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.platform.async.application.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.domain.AsyncAction;
import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;
import com.xiyu.bid.platform.async.domain.ExponentialBackoffRetrySchedule;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryJobService {
    private static final int MAX_ATTEMPTS = 3;

    private final NotificationDeliveryTaskRepository taskRepository;
    private final NotificationDeliveryDlqRepository dlqRepository;
    private final OutboundLogRepository outboundLogRepository;
    private final WeComPushService pushService;
    private final NotificationFailureClassifier failureClassifier;
    private final AsyncDecisionResolver decisionResolver;
    private final AsyncObservabilityRecorder observabilityRecorder;
    private final ObjectMapper objectMapper;
    private final ExponentialBackoffRetrySchedule retrySchedule = new ExponentialBackoffRetrySchedule(60, 300, 3);

    @Scheduled(fixedDelayString = "${notification.wecom.worker-fixed-delay-ms:5000}")
    public void processDueTasks() {
        List<NotificationDeliveryTask> tasks = taskRepository.findRunnableTasks(LocalDateTime.now());
        tasks.forEach(this::processTaskSafely);
    }

    @Transactional
    public void processTaskSafely(NotificationDeliveryTask task) {
        NotificationDeliveryTask managedTask = taskRepository.findById(task.getId()).orElse(task);
        managedTask.setStatus(NotificationDeliveryTaskStatus.PROCESSING);
        managedTask.setUpdatedAt(LocalDateTime.now());
        NotificationDeliveryCommand command = deserialize(managedTask.getPayload());

        try {
            NotificationDeliveryResult result = pushService.push(command);
            if (result.successful()) {
                handleSuccess(managedTask, command, result);
                return;
            }
            handleFailure(managedTask, command, result.message());
        } catch (RuntimeException ex) {
            handleFailure(managedTask, command, ex.getMessage());
        }
    }

    private void handleSuccess(
            NotificationDeliveryTask task,
            NotificationDeliveryCommand command,
            NotificationDeliveryResult result
    ) {
        task.setStatus(NotificationDeliveryTaskStatus.DELIVERED);
        task.setNextRetryAt(null);
        task.setLastErrorCode(null);
        task.setLastErrorMessage(null);
        taskRepository.save(task);
        outboundLogRepository.save(OutboundLog.builder()
                .notificationId(command.notificationId())
                .userId(command.recipientUserId())
                .channel(OutboundChannel.WECOM)
                .status(result.skipped() ? OutboundStatus.SKIPPED : OutboundStatus.SENT)
                .skipReason(result.skipped() ? SkipReason.NOT_BOUND : null)
                .wecomErrcode(result.errcode())
                .wecomErrmsg(trim(result.message()))
                .attemptCount(task.getAttemptCount())
                .build());
        observabilityRecorder.recordSuccess("notification", task.getEventType(), task.getBusinessKey());
    }

    private void handleFailure(NotificationDeliveryTask task, NotificationDeliveryCommand command, String errorMessage) {
        AsyncHandlingDecision decision = decisionResolver.resolve(
                failureClassifier.classify(new RuntimeException(errorMessage)),
                task.getAttemptCount() + 1,
                MAX_ATTEMPTS,
                retrySchedule,
                true
        );
        applyFailureDecision(task, command, decision, errorMessage);
    }

    private void applyFailureDecision(
            NotificationDeliveryTask task,
            NotificationDeliveryCommand command,
            AsyncHandlingDecision decision,
            String errorMessage
    ) {
        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setLastErrorCode(decision.reasonCode());
        task.setLastErrorMessage(trim(errorMessage));
        task.setUpdatedAt(LocalDateTime.now());

        switch (decision.action()) {
            case RETRY -> {
                task.setStatus(NotificationDeliveryTaskStatus.PENDING_RETRY);
                task.setNextRetryAt(LocalDateTime.now().plusSeconds(decision.nextRetryDelaySeconds()));
                taskRepository.save(task);
                observabilityRecorder.recordRetry("notification", task.getEventType(), task.getBusinessKey(), nextAttempt, decision);
            }
            case DEAD_LETTER -> {
                task.setStatus(NotificationDeliveryTaskStatus.DEAD_LETTER);
                task.setNextRetryAt(null);
                taskRepository.save(task);
                dlqRepository.save(NotificationDeliveryDlq.builder()
                        .taskId(task.getId())
                        .notificationId(command.notificationId())
                        .recipientUserId(command.recipientUserId())
                        .businessKey(task.getBusinessKey())
                        .reasonCode(decision.reasonCode())
                        .errorMessage(trim(errorMessage))
                        .payload(task.getPayload())
                        .build());
                observabilityRecorder.recordDeadLetter("notification", task.getEventType(), task.getBusinessKey(), decision.reasonCode());
            }
            case DROP, SUCCEED_WITH_LOG -> {
                task.setStatus(NotificationDeliveryTaskStatus.DELIVERED);
                task.setNextRetryAt(null);
                taskRepository.save(task);
                observabilityRecorder.recordDrop("notification", task.getEventType(), task.getBusinessKey(), decision.reasonCode(), decision.alertRequired());
            }
            case FAIL_MAIN_TRANSACTION -> throw new IllegalStateException("Notification async side effect should not fail main transaction");
        }

        outboundLogRepository.save(OutboundLog.builder()
                .notificationId(command.notificationId())
                .userId(command.recipientUserId())
                .channel(OutboundChannel.WECOM)
                .status(decision.action() == AsyncAction.RETRY ? OutboundStatus.FAILED : OutboundStatus.SKIPPED)
                .skipReason(decision.action() == AsyncAction.RETRY ? SkipReason.ERROR : SkipReason.NOT_BOUND)
                .wecomErrmsg(trim(errorMessage))
                .attemptCount(nextAttempt)
                .build());
    }

    private NotificationDeliveryCommand deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationDeliveryCommand.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize notification delivery command", ex);
        }
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
