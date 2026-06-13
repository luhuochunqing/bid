package com.xiyu.bid.notification.outbound.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTask;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskRepository;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskStatus;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryDlqRepository;
import com.xiyu.bid.notification.outbound.repository.OutboundLogRepository;
import com.xiyu.bid.notification.outbound.service.NotificationDeliveryResult;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDeliveryJobService — retry / dlq decisions")
class NotificationDeliveryJobServiceTest {

    @Mock private NotificationDeliveryTaskRepository taskRepository;
    @Mock private NotificationDeliveryDlqRepository dlqRepository;
    @Mock private OutboundLogRepository outboundLogRepository;
    @Mock private WeComPushService pushService;
    @Mock private NotificationFailureClassifier failureClassifier;
    @Mock private AsyncDecisionResolver decisionResolver;
    @Mock private AsyncObservabilityRecorder observabilityRecorder;

    @Test
    @DisplayName("successful result -> delivered and success metric")
    void successfulResult_Delivered() {
        NotificationDeliveryJobService jobService = jobService();
        NotificationDeliveryTask task = task();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(pushService.push(any())).thenReturn(NotificationDeliveryResult.success(0, "ok"));

        jobService.processTaskSafely(task);

        ArgumentCaptor<NotificationDeliveryTask> captor = ArgumentCaptor.forClass(NotificationDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryTaskStatus.DELIVERED);
        verify(observabilityRecorder).recordSuccess(eq("notification"), eq("notification.wecom_push"), eq("1:7:MENTION"));
    }

    @Test
    @DisplayName("transient failure -> pending retry and retry metric")
    void transientFailure_Retry() {
        NotificationDeliveryJobService jobService = jobService();
        NotificationDeliveryTask task = task();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(pushService.push(any())).thenThrow(new RuntimeException("timeout"));
        when(failureClassifier.classify(any())).thenReturn(AsyncFailureKind.TRANSIENT_DEPENDENCY);
        when(decisionResolver.resolve(any(), eq(1), eq(3), any(), eq(true)))
                .thenReturn(com.xiyu.bid.platform.async.domain.AsyncHandlingDecision.retry("TRANSIENT_DEPENDENCY", 60, false));

        jobService.processTaskSafely(task);

        ArgumentCaptor<NotificationDeliveryTask> captor = ArgumentCaptor.forClass(NotificationDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryTaskStatus.PENDING_RETRY);
        verify(observabilityRecorder).recordRetry(eq("notification"), eq("notification.wecom_push"), eq("1:7:MENTION"), eq(1), any());
    }

    @Test
    @DisplayName("contract failure -> dead letter and dlq record")
    void contractFailure_Dlq() {
        NotificationDeliveryJobService jobService = jobService();
        NotificationDeliveryTask task = task();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(pushService.push(any())).thenThrow(new RuntimeException("invalid template"));
        when(failureClassifier.classify(any())).thenReturn(AsyncFailureKind.CONTRACT_INVALID);
        when(decisionResolver.resolve(any(), eq(1), eq(3), any(), eq(true)))
                .thenReturn(com.xiyu.bid.platform.async.domain.AsyncHandlingDecision.deadLetter("CONTRACT_INVALID", true));

        jobService.processTaskSafely(task);

        ArgumentCaptor<NotificationDeliveryTask> captor = ArgumentCaptor.forClass(NotificationDeliveryTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryTaskStatus.DEAD_LETTER);
        verify(dlqRepository).save(any());
        verify(observabilityRecorder).recordDeadLetter(eq("notification"), eq("notification.wecom_push"), eq("1:7:MENTION"), eq("CONTRACT_INVALID"));
        verify(observabilityRecorder, never()).recordRetry(eq("notification"), any(), any(), any(Integer.class), any());
    }

    private NotificationDeliveryJobService jobService() {
        return new NotificationDeliveryJobService(
                taskRepository,
                dlqRepository,
                outboundLogRepository,
                pushService,
                failureClassifier,
                decisionResolver,
                observabilityRecorder,
                new ObjectMapper()
        );
    }

    private NotificationDeliveryTask task() {
        return NotificationDeliveryTask.builder()
                .id(1L)
                .notificationId(1L)
                .recipientUserId(7L)
                .eventType("notification.wecom_push")
                .businessKey("1:7:MENTION")
                .payload("{\"notificationId\":1,\"recipientUserId\":7,\"type\":\"MENTION\",\"title\":\"t\",\"sourceEntityType\":\"PROJECT\",\"sourceEntityId\":3}")
                .status(NotificationDeliveryTaskStatus.PENDING)
                .attemptCount(0)
                .build();
    }
}
