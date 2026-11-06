package com.xiyu.bid.notification;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryJobService;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTask;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskRepository;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskStatus;
import com.xiyu.bid.notification.outbound.repository.OutboundLogRepository;
import com.xiyu.bid.notification.outbound.service.NotificationDeliveryResult;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.test.AbstractTransactionBoundaryTest;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.service.WarehouseExpiryScanTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ReminderConcurrencyTestConfig.class)
@ActiveProfiles("reminder-concurrency-test")
@Transactional(propagation = Propagation.NEVER)
class ReminderConcurrencyIntegrationTest extends AbstractTransactionBoundaryTest {
    @Autowired WarehouseRepository warehouses;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryTaskRepository tasks;
    @Autowired OutboundLogRepository logs;
    @Autowired UserRepository users;
    @Autowired NotificationApplicationService notificationService;
    @Autowired WeComPushService push;
    @Autowired WarehouseExpiryScanTask scanner;
    @Autowired NotificationDeliveryJobService worker;
    @Autowired ReminderConcurrencyTestConfig.TransactionalPushDependency pushDependency;

    @BeforeEach
    void setup() {
        reset(users, notificationService, push);
        logs.deleteAll();
        tasks.deleteAll();
        notifications.deleteAll();
        warehouses.deleteAll();
        when(users.findEnabledByRoleProfileCodes(anyList())).thenReturn(List.of(User.builder().id(7L).build()));
        when(users.findByFullName(any())).thenReturn(List.of());
    }

    @Test
    void overlappingScansCreateOnlyOneNotification() throws Exception {
        warehouse();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var duplicateEntered = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(notificationService.createNotification(any(), any())).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } else {
                duplicateEntered.countDown();
            }
            return persistNotification(call.getArgument(0));
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(scanner::processScan);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(scanner::processScan);
                duplicateEntered.await(500, TimeUnit.MILLISECONDS);
                release.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            } finally {
                release.countDown();
            }
        }
        assertThat(notifications.count()).isEqualTo(1);
        verify(notificationService, times(1)).createNotification(any(), any());
    }

    @Test
    void overlappingWorkersWithSameStaleCandidateSendOnlyOnce() throws Exception {
        NotificationDeliveryTask task = task();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var duplicateEntered = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(push.push(any())).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } else {
                duplicateEntered.countDown();
            }
            return NotificationDeliveryResult.success(0, "ok");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> worker.processTaskSafely(task));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> worker.processTaskSafely(task));
                duplicateEntered.await(500, TimeUnit.MILLISECONDS);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(calls.get()).isEqualTo(1);
        assertThat(logs.count()).isEqualTo(1);
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(NotificationDeliveryTaskStatus.DELIVERED);
    }

    @Test
    void staleCandidateCannotBypassFutureRetryOrDeliveredStatus() {
        NotificationDeliveryTask stale = task();
        var current = tasks.findById(stale.getId()).orElseThrow();
        current.setStatus(NotificationDeliveryTaskStatus.PENDING_RETRY);
        current.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        tasks.saveAndFlush(current);
        worker.processTaskSafely(stale);
        verifyNoInteractions(push);
        current.setStatus(NotificationDeliveryTaskStatus.DELIVERED);
        tasks.saveAndFlush(current);
        worker.processTaskSafely(stale);
        verifyNoInteractions(push);
        tasks.deleteById(current.getId());
        worker.processTaskSafely(stale);
        verifyNoInteractions(push);
    }

    @Test
    void notificationFailureRollsBackStatusAndAllowsLaterScan() {
        var wh = warehouse();
        wh.setStatus(WarehouseStatus.IN_USE);
        warehouses.saveAndFlush(wh);
        when(notificationService.createNotification(any(), any())).thenAnswer(call -> {
            persistNotification(call.getArgument(0));
            throw new IllegalStateException("write failed");
        });
        assertThat(scanner.processScan()).isZero();
        assertThat(warehouses.findById(wh.getId()).orElseThrow().getStatus()).isEqualTo(WarehouseStatus.IN_USE);
        assertThat(notifications.count()).isZero();
        doAnswer(call -> persistNotification(call.getArgument(0))).when(notificationService).createNotification(any(), any());
        assertThat(scanner.processScan()).isEqualTo(1);
        assertThat(scanner.processScan()).isZero();
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void retryIsSavedAndOnlyRunsAgainWhenDue() {
        var stale = task();
        when(push.push(any())).thenThrow(new IllegalStateException("timeout"));
        worker.processTaskSafely(stale);
        var stored = tasks.findById(stale.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryTaskStatus.PENDING_RETRY);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getNextRetryAt()).isAfter(LocalDateTime.now());
        worker.processTaskSafely(stale);
        verify(push, times(1)).push(any());
        stored.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        tasks.saveAndFlush(stored);
        doReturn(NotificationDeliveryResult.success(0, "ok")).when(push).push(any());
        worker.processTaskSafely(stale);
        worker.processTaskSafely(stale);
        assertThat(tasks.findById(stale.getId()).orElseThrow().getStatus()).isEqualTo(NotificationDeliveryTaskStatus.DELIVERED);
        assertThat(logs.count()).isEqualTo(2);
        verify(push, times(2)).push(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-7, 15})
    void rejectedNotificationRollsBackOnlyFailedWarehouseAndContinues(int daysUntilExpiry) {
        warehouse();
        warehouse();
        // Use the repository's actual candidate order to place the rejected warehouse first.
        var candidates = warehouses.findAll();
        var rejected = candidates.getFirst();
        var healthy = candidates.getLast();
        for (var wh : candidates) {
            wh.setStatus(WarehouseStatus.IN_USE);
            wh.setEndDate(LocalDate.now().plusDays(daysUntilExpiry));
        }
        rejected.setName("仓".repeat(200));
        warehouses.saveAllAndFlush(candidates);
        when(notificationService.createNotification(any(), any())).thenAnswer(call -> {
            CreateNotificationRequest request = call.getArgument(0);
            var result = com.xiyu.bid.notification.core.NotificationDispatchPolicy.validateDispatch(
                    com.xiyu.bid.notification.core.NotificationType.SYSTEM, 7L, request.title(), request.body());
            return result.isValid() ? persistNotification(request) : result;
        });

        assertThat(scanner.processScan()).isEqualTo(1);
        assertThat(warehouses.findById(rejected.getId()).orElseThrow().getStatus()).isEqualTo(WarehouseStatus.IN_USE);
        assertThat(warehouses.findById(healthy.getId()).orElseThrow().getStatus())
                .isEqualTo(daysUntilExpiry < 0 ? WarehouseStatus.EXPIRED : WarehouseStatus.EXPIRING);
        assertThat(notifications.findAll()).extracting(Notification::getSourceEntityId).containsExactly(healthy.getId());
        assertThat(scanner.processScan()).isZero();
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void transactionalPushDependencyFailureStillCommitsRetry() {
        var stale = task();
        when(push.push(any())).thenAnswer(call -> pushDependency.fail());
        worker.processTaskSafely(stale);
        var stored = tasks.findById(stale.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryTaskStatus.PENDING_RETRY);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getNextRetryAt()).isAfter(LocalDateTime.now());
        assertThat(logs.count()).isEqualTo(1);
        worker.processTaskSafely(stale);
        verify(push, times(1)).push(any());
    }

    @Test
    void terminalOrProcessingTasksAreNeverSent() {
        var stale = task();
        for (var state : List.of(NotificationDeliveryTaskStatus.DELIVERED, NotificationDeliveryTaskStatus.DEAD_LETTER,
                NotificationDeliveryTaskStatus.PROCESSING)) {
            var stored = tasks.findById(stale.getId()).orElseThrow();
            stored.setStatus(state);
            tasks.saveAndFlush(stored);
            worker.processTaskSafely(stale);
        }
        verifyNoInteractions(push);
        assertThat(logs.count()).isZero();
    }

    private DispatchResult persistNotification(CreateNotificationRequest request) {
        var saved = notifications.save(Notification.builder().type(request.type())
                .sourceEntityType(request.sourceEntityType()).sourceEntityId(request.sourceEntityId())
                .title(request.title()).createdBy(1L).build());
        return DispatchResult.validWithId(saved.getId());
    }

    private WarehouseEntity warehouse() {
        return warehouses.saveAndFlush(WarehouseEntity.builder().name("银川仓").type(WarehouseType.SELF_OPERATED)
                .region("西北").province("宁夏").address("测试地址").area(BigDecimal.TEN).contactPerson("联系人")
                .startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().minusDays(109))
                .lessor("出租方").lessee("承租方").hasPropertyCert(true).hasInvoice(true).hasPhotos(true)
                .hasLeaseContract(true).status(WarehouseStatus.EXPIRED).build());
    }

    private NotificationDeliveryTask task() {
        return tasks.saveAndFlush(NotificationDeliveryTask.builder().notificationId(1L).recipientUserId(7L)
                .eventType("notification.wecom_push").businessKey("1:7:SYSTEM")
                .payload("{\"notificationId\":1,\"recipientUserId\":7,\"type\":\"SYSTEM\",\"title\":\"test\"}")
                .status(NotificationDeliveryTaskStatus.PENDING).build());
    }
}
