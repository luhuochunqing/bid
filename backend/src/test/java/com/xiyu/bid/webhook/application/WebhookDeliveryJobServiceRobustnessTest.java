package com.xiyu.bid.webhook.application;

import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.domain.AsyncFailureKind;
import com.xiyu.bid.platform.async.domain.AsyncHandlingDecision;
import com.xiyu.bid.platform.async.domain.AsyncAction;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 鲁棒性测试：外部依赖超时 / 异常场景。
 * 覆盖 WebhookDeliveryJobService 的 HTTP 异常分类、重试决策和 DLQ 落库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDeliveryJobService — external dependency robustness")
class WebhookDeliveryJobServiceRobustnessTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private WebhookDeliveryTaskRepository taskRepository;
    @Mock
    private WebhookDeliveryLogRepository logRepository;
    @Mock
    private WebhookDeliveryDlqRepository dlqRepository;
    @Mock
    private WebhookHttpSender httpSender;
    @Mock
    private AsyncObservabilityRecorder observabilityRecorder;

    private WebhookDeliveryJobService jobService;
    private ExponentialBackoffRetrySchedule retrySchedule;
    private WebhookFailureClassifier failureClassifier;
    private AsyncDecisionResolver decisionResolver;

    private void initService() {
        retrySchedule = new ExponentialBackoffRetrySchedule(2, 60, 4);
        failureClassifier = new WebhookFailureClassifier();
        decisionResolver = new AsyncDecisionResolver();
        jobService = new WebhookDeliveryJobService(
                taskRepository, logRepository, dlqRepository,
                httpSender, failureClassifier, decisionResolver, observabilityRecorder
        );
    }

    // ========== HTTP 异常分类边界测试 ==========

    @Nested
    @DisplayName("HTTP 异常分类 — 使用真实 WebhookFailureClassifier")
    class HttpExceptionClassification {

        private WebhookFailureClassifier classifier;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            classifier = new WebhookFailureClassifier();
        }

        @org.junit.jupiter.api.Test
        @DisplayName("408 状态码 → CONTRACT_INVALID（>= 400 但不是 429）")
        void statusCode_408_classifiedAsContractInvalid() {
            assertThat(classifier.classifyStatusCode(408)).isEqualTo(AsyncFailureKind.CONTRACT_INVALID);
        }

        @org.junit.jupiter.api.Test
        @DisplayName("429 状态码 → TRANSIENT_DEPENDENCY（显式分支）")
        void statusCode_429_classifiedAsTransient() {
            assertThat(classifier.classifyStatusCode(429)).isEqualTo(AsyncFailureKind.TRANSIENT_DEPENDENCY);
        }

        @org.junit.jupiter.api.Test
        @DisplayName("500 状态码 → TRANSIENT_DEPENDENCY（>= 500 分支）")
        void statusCode_500_classifiedAsTransient() {
            assertThat(classifier.classifyStatusCode(500)).isEqualTo(AsyncFailureKind.TRANSIENT_DEPENDENCY);
        }

        @org.junit.jupiter.api.Test
        @DisplayName("502/503/504 状态码 → TRANSIENT_DEPENDENCY")
        void statusCode_5xx_classifiedAsTransient() {
            for (int code : new int[]{502, 503, 504}) {
                assertThat(classifier.classifyStatusCode(code))
                        .as("HTTP %d should be TRANSIENT_DEPENDENCY", code)
                        .isEqualTo(AsyncFailureKind.TRANSIENT_DEPENDENCY);
            }
        }

        @org.junit.jupiter.api.Test
        @DisplayName("400/401/403/404 状态码 → CONTRACT_INVALID")
        void statusCode_4xx_classifiedAsContractInvalid() {
            for (int code : new int[]{400, 401, 403, 404}) {
                assertThat(classifier.classifyStatusCode(code))
                        .as("HTTP %d should be CONTRACT_INVALID", code)
                        .isEqualTo(AsyncFailureKind.CONTRACT_INVALID);
            }
        }

        @org.junit.jupiter.api.Test
        @DisplayName("2xx 状态码 → BUSINESS_REJECT（< 400 default 分支）")
        void statusCode_2xx_classifiedAsBusinessReject() {
            for (int code : new int[]{200, 201, 204}) {
                assertThat(classifier.classifyStatusCode(code))
                        .as("HTTP %d should be BUSINESS_REJECT", code)
                        .isEqualTo(AsyncFailureKind.BUSINESS_REJECT);
            }
        }

        @org.junit.jupiter.api.Test
        @DisplayName("0 状态码（null 映射） → BUSINESS_REJECT（0 < 400，落在 default 分支）")
        void zeroStatusCode_classifiedAsBusinessReject() {
            assertThat(classifier.classifyStatusCode(0)).isEqualTo(AsyncFailureKind.BUSINESS_REJECT);
        }

        @org.junit.jupiter.api.Test
        @DisplayName("连接超时异常 → TRANSIENT_DEPENDENCY")
        void connectionTimeoutException_classifiedAsTransient() {
            assertThat(classifier.classify(new ConnectException("Connection refused")))
                    .isEqualTo(AsyncFailureKind.TRANSIENT_DEPENDENCY);
        }

        @org.junit.jupiter.api.Test
        @DisplayName("HTTP 连接超时异常 → TRANSIENT_DEPENDENCY")
        void httpConnectTimeoutException_classifiedAsTransient() {
            assertThat(classifier.classify(new HttpConnectTimeoutException("Timeout")))
                    .isEqualTo(AsyncFailureKind.TRANSIENT_DEPENDENCY);
        }

        @org.junit.jupiter.api.Test
        @DisplayName("未知异常类型 → BUG（不重试）")
        void unknownException_classifiedAsBug() {
            assertThat(classifier.classify(new RuntimeException("unexpected")))
                    .isEqualTo(AsyncFailureKind.BUG);
        }
    }

    // ========== 重试决策边界测试 ==========

    @Nested
    @DisplayName("重试决策边界")
    class RetryDecisionBoundary {

        @Test
        @DisplayName("瞬态错误：未达上限 → RETRY with backoff")
        void transientError_beforeMaxRetries_retryWithBackoff() {
            ExponentialBackoffRetrySchedule localSchedule = new ExponentialBackoffRetrySchedule(2, 60, 4);
            AsyncHandlingDecision decision = new AsyncDecisionResolver().resolve(
                    AsyncFailureKind.TRANSIENT_DEPENDENCY,
                    1,
                    MAX_ATTEMPTS,
                    localSchedule,
                    true
            );

            assertThat(decision.action()).isEqualTo(AsyncAction.RETRY);
            assertThat(decision.nextRetryDelaySeconds()).isPositive();
            assertThat(decision.alertRequired()).isFalse();
        }

        @Test
        @DisplayName("瞬态错误：已达上限 → DEAD_LETTER")
        void transientError_atMaxRetries_deadLetter() {
            ExponentialBackoffRetrySchedule localSchedule = new ExponentialBackoffRetrySchedule(2, 60, 4);
            AsyncHandlingDecision decision = new AsyncDecisionResolver().resolve(
                    AsyncFailureKind.TRANSIENT_DEPENDENCY,
                    MAX_ATTEMPTS,
                    MAX_ATTEMPTS,
                    localSchedule,
                    true
            );

            assertThat(decision.action()).isEqualTo(AsyncAction.DEAD_LETTER);
            assertThat(decision.alertRequired()).isTrue();
        }

        @Test
        @DisplayName("契约错误：立即 DEAD_LETTER，不重试")
        void contractError_immediateDeadLetter() {
            ExponentialBackoffRetrySchedule localSchedule = new ExponentialBackoffRetrySchedule(2, 60, 4);
            AsyncHandlingDecision decision = new AsyncDecisionResolver().resolve(
                    AsyncFailureKind.CONTRACT_INVALID,
                    1,
                    MAX_ATTEMPTS,
                    localSchedule,
                    true
            );

            assertThat(decision.action()).isEqualTo(AsyncAction.DEAD_LETTER);
            assertThat(decision.alertRequired()).isTrue();
        }

        @Test
        @DisplayName("可选项错误：deadLetterSupported=false → DROP，不产生告警")
        void optionalError_dropWithoutAlert() {
            ExponentialBackoffRetrySchedule localSchedule = new ExponentialBackoffRetrySchedule(2, 60, 4);
            AsyncHandlingDecision decision = new AsyncDecisionResolver().resolve(
                    AsyncFailureKind.SIDE_EFFECT_OPTIONAL,
                    1,
                    MAX_ATTEMPTS,
                    localSchedule,
                    false
            );

            assertThat(decision.action()).isEqualTo(AsyncAction.DROP);
            assertThat(decision.alertRequired()).isFalse();
        }
    }

    // ========== processTaskSafely 集成测试 ==========

    @Nested
    @DisplayName("processTaskSafely 集成")
    class ProcessTaskSafelyIntegration {

        @Test
        @DisplayName("任务不存在时使用原始 task 继续处理，不抛出异常")
        void taskNotFound_usesOriginalTask_noException() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            when(taskRepository.findById(1L)).thenReturn(Optional.empty());
            when(httpSender.send(any(), any()))
                    .thenReturn(WebhookSendResult.success(200, "{}", LocalDateTime.now()));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WebhookDeliveryTaskStatus.DELIVERED);
        }

        @Test
        @DisplayName("HTTP 429：瞬态错误 → PENDING_RETRY")
        void http429_transient_retry() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(0);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenReturn(WebhookSendResult.failure(429, "rate limited", "HTTP_429", LocalDateTime.now()));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING_RETRY);
            verify(logRepository).save(any(WebhookDeliveryLog.class));
        }

        @Test
        @DisplayName("HTTP 400：契约错误 → DEAD_LETTER")
        void http400_contractError_deadLetter() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(0);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenReturn(WebhookSendResult.failure(400, "bad request", "HTTP_400", LocalDateTime.now()));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WebhookDeliveryTaskStatus.DEAD_LETTER);
            verify(dlqRepository).save(any(WebhookDeliveryDlq.class));
            verify(logRepository).save(any(WebhookDeliveryLog.class));
        }

        @Test
        @DisplayName("连接超时 → 瞬态错误 → PENDING_RETRY")
        void connectionTimeout_transient_retry() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(0);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenThrow(new ConnectException("Connection refused"));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WebhookDeliveryTaskStatus.PENDING_RETRY);
        }

        @Test
        @DisplayName("错误消息过长时截断到 1000 字符")
        void errorMessage_truncatedTo1000chars() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(0);
            String longError = "x".repeat(2000);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenThrow(new RuntimeException(longError));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository).save(captor.capture());
            String errorMsg = captor.getValue().getLastErrorMessage();
            assertThat(errorMsg).isNotNull();
            assertThat(errorMsg.length()).isLessThanOrEqualTo(1000);
        }

        @Test
        @DisplayName("HTTP 500：达到最大重试次数后进入 DEAD_LETTER")
        void http500_maxRetries_reached_deadLetter() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(MAX_ATTEMPTS - 1);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenReturn(WebhookSendResult.failure(500, "server error", "HTTP_500", LocalDateTime.now()));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WebhookDeliveryTaskStatus.DEAD_LETTER);
            assertThat(captor.getValue().getNextRetryAt()).isNull();
            verify(dlqRepository).save(any(WebhookDeliveryDlq.class));
        }

        @Test
        @DisplayName("每次处理都记录 WebhookDeliveryLog")
        void everyAttempt_logsToWebhookDeliveryLog() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(0);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenReturn(WebhookSendResult.failure(500, "error", "HTTP_500", LocalDateTime.now()));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryLog> captor = ArgumentCaptor.forClass(WebhookDeliveryLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getTenderId()).isEqualTo(task.getTenderId());
            assertThat(captor.getValue().getTargetUrl()).isEqualTo(task.getTargetUrl());
            assertThat(captor.getValue().getStatusCode()).isEqualTo(500);
        }

        @Test
        @DisplayName("HTTP 200：成功投递，状态更新为 DELIVERED")
        void http200_success_delivered() throws Exception {
            initService();
            WebhookDeliveryTask task = makeTask(1L);
            task.setAttemptCount(0);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(httpSender.send(any(), any()))
                    .thenReturn(WebhookSendResult.success(200, "{\"ok\":true}", LocalDateTime.now()));

            jobService.processTaskSafely(task);

            ArgumentCaptor<WebhookDeliveryTask> captor = ArgumentCaptor.forClass(WebhookDeliveryTask.class);
            verify(taskRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WebhookDeliveryTaskStatus.DELIVERED);
            assertThat(captor.getValue().getLastErrorCode()).isNull();
            assertThat(captor.getValue().getLastErrorMessage()).isNull();
        }
    }

    // ========== 辅助方法 ==========

    private WebhookDeliveryTask makeTask(Long id) {
        return WebhookDeliveryTask.builder()
                .id(id)
                .tenderId(100L)
                .targetUrl("https://example.com/webhook")
                .payload("{\"event\":\"test\"}")
                .eventType("tender.created")
                .businessKey("100:admin")
                .status(WebhookDeliveryTaskStatus.PENDING)
                .attemptCount(0)
                .build();
    }
}
