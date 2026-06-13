package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.domain.ExponentialBackoffRetrySchedule;
import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.tenderupload.entity.TenderFile;
import com.xiyu.bid.tenderupload.entity.TenderFileUploadStatus;
import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskDlq;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import com.xiyu.bid.tenderupload.repository.TenderFileRepository;
import com.xiyu.bid.tenderupload.repository.TenderTaskDlqRepository;
import com.xiyu.bid.tenderupload.repository.TenderTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 鲁棒性测试：并发 claim / 重复消费场景。
 * 覆盖 TenderTaskWorkerService 的并发安全边界。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenderTaskWorkerService — concurrency & idempotency robustness")
class TenderTaskWorkerServiceRobustnessTest {

    @Mock
    private TenderTaskRepository tenderTaskRepository;
    @Mock
    private TenderTaskDlqRepository tenderTaskDlqRepository;
    @Mock
    private TenderFileRepository tenderFileRepository;
    @Mock
    private StorageGuardService storageGuardService;
    @Mock
    private TenderTaskFailureClassifier failureClassifier;
    @Mock
    private TenderTaskClaimLock claimLock;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Executor applicationTaskExecutor;

    private TenderProcessingProperties properties;
    private TenderTaskStateMachine stateMachine;
    private AsyncDecisionResolver decisionResolver;
    private TenderTaskWorkerService workerService;

    @BeforeEach
    void setUp() {
        properties = new TenderProcessingProperties();
        properties.setMaxRetries(3);
        properties.setRetryDelaysMinutes(List.of(1, 5, 15));
        properties.setMaxGlobalConcurrency(4);
        properties.setMaxPerUserConcurrency(2);
        stateMachine = new TenderTaskStateMachine(properties);
        decisionResolver = new AsyncDecisionResolver();
        workerService = new TenderTaskWorkerService(
                properties,
                tenderTaskRepository,
                tenderTaskDlqRepository,
                tenderFileRepository,
                stateMachine,
                failureClassifier,
                decisionResolver,
                storageGuardService,
                claimLock,
                transactionTemplate,
                applicationTaskExecutor
        );
    }

    // ========== claim 幂等性测试 ==========

    @Nested
    @DisplayName("claim 幂等性")
    class ClaimIdempotency {

        @Test
        @DisplayName("claimRunnableTasks：全局锁获取失败时返回空列表，不阻塞其他 worker")
        void claimRunnableTasks_lockAcquisitionFailed_returnsEmptyList() {
            when(claimLock.tryAcquire()).thenReturn(false);

            List<TenderTask> result = workerService.claimRunnableTasks("worker-1", 10);

            assertThat(result).isEmpty();
            verify(tenderTaskRepository, never()).claimRunnableTaskIds(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("claimRunnableTasks：limit <= 0 时返回空列表")
        void claimRunnableTasks_invalidLimit_returnsEmpty() {
            List<TenderTask> result = workerService.claimRunnableTasks("worker-1", 0);
            assertThat(result).isEmpty();
            verify(claimLock, never()).tryAcquire();
        }

        @Test
        @DisplayName("claimRunnableTasks：资源保护生效时返回空列表")
        void claimRunnableTasks_globalConcurrencyFull_returnsEmpty() {
            when(claimLock.tryAcquire()).thenReturn(true);
            when(tenderTaskRepository.countByStatus(TenderTaskStatus.RUNNING)).thenReturn(4L);
            properties.setMaxGlobalConcurrency(4);

            List<TenderTask> result = workerService.claimRunnableTasks("worker-1", 10);

            assertThat(result).isEmpty();
            verify(tenderTaskRepository, never()).claimRunnableTaskIds(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("claimRunnableTasks：锁始终在 finally 中释放")
        void claimRunnableTasks_lockAlwaysReleased() {
            when(claimLock.tryAcquire()).thenReturn(true);
            when(tenderTaskRepository.countByStatus(TenderTaskStatus.RUNNING)).thenReturn(0L);
            when(tenderTaskRepository.claimRunnableTaskIds(any(), anyInt(), anyInt())).thenReturn(List.of());
            properties.setMaxGlobalConcurrency(10);

            workerService.claimRunnableTasks("worker-1", 10);

            verify(claimLock, times(1)).tryAcquire();
            verify(claimLock, times(1)).release();
        }
    }

    // ========== processTask 幂等性测试 ==========

    @Nested
    @DisplayName("processTask 幂等性")
    class ProcessTaskIdempotency {

        private TenderTask makeTask(Long id, String workerId) {
            TenderFile file = TenderFile.builder()
                    .id(id != null ? id : 10L)
                    .userId(1L)
                    .filePath("2026/05/test.pdf")
                    .build();
            TenderTask task = TenderTask.builder()
                    .id(id != null ? id : 20L)
                    .file(file)
                    .status(TenderTaskStatus.RUNNING)
                    .lockedBy(workerId != null ? workerId : "worker-1")
                    .attempts(0)
                    .priority(5)
                    .availableAt(LocalDateTime.now())
                    .build();
            task.setLockedAt(LocalDateTime.now());
            return task;
        }

        @Test
        @DisplayName("processTask：任务状态非 RUNNING 时跳过处理，防止重复消费")
        void processTask_nonRunningStatus_skipsProcessing() {
            TenderTask task = makeTask(101L, "worker-1");
            task.setStatus(TenderTaskStatus.SUCCEEDED);
            when(tenderTaskRepository.findById(101L)).thenReturn(Optional.of(task));

            workerService.processTask(101L, "worker-1");

            verify(storageGuardService, never()).resolveAndValidate(any());
            verify(tenderTaskRepository, never()).save(any(TenderTask.class));
        }

        @Test
        @DisplayName("processTask：workerId 不匹配时跳过处理，防止其他 worker 错误认领")
        void processTask_workerIdMismatch_skipsProcessing() {
            TenderTask task = makeTask(102L, "worker-1");
            when(tenderTaskRepository.findById(102L)).thenReturn(Optional.of(task));

            workerService.processTask(102L, "worker-2");

            verify(storageGuardService, never()).resolveAndValidate(any());
        }

        @Test
        @DisplayName("processTask：任务不存在时抛出明确的 IllegalArgumentException")
        void processTask_taskNotFound_throwsIllegalArgument() {
            when(tenderTaskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> workerService.processTask(999L, "worker-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("任务不存在");
        }

        @Test
        @DisplayName("processTask：文件缺失时抛 IllegalStateException，进入错误处理路径并 save")
        void processTask_fileMissing_triggersErrorHandling() {
            TenderTask task = makeTask(103L, "worker-1");
            task.setFile(null);
            when(tenderTaskRepository.findById(103L)).thenReturn(Optional.of(task));
            when(failureClassifier.classify(any())).thenReturn(
                    com.xiyu.bid.platform.async.domain.AsyncFailureKind.TRANSIENT_DEPENDENCY);

            workerService.processTask(103L, "worker-1");

            // 进入错误处理路径，至少调用一次 save
            verify(tenderTaskRepository).save(any(TenderTask.class));
        }

        @Test
        @DisplayName("processTask：storageGuardService 失败时调用 failureClassifier 并走决策路径")
        void processTask_storageFailure_classifiedAndHandled() {
            TenderTask task = makeTask(104L, "worker-1");
            // sha256 为 null 会触发计算并失败
            task.getFile().setFileSha256(null);
            when(tenderTaskRepository.findById(104L)).thenReturn(Optional.of(task));
            when(storageGuardService.resolveAndValidate(anyString())).thenReturn(Path.of("/tmp/missing.pdf"));
            doNothing().when(storageGuardService).ensureExists(any());
            when(storageGuardService.fileSize(any())).thenReturn(1000L);
            when(storageGuardService.sha256(any())).thenThrow(new RuntimeException("sidecar unavailable"));
            when(failureClassifier.classify(any())).thenReturn(
                    com.xiyu.bid.platform.async.domain.AsyncFailureKind.TRANSIENT_DEPENDENCY);

            workerService.processTask(104L, "worker-1");

            verify(failureClassifier).classify(any());
            verify(tenderTaskRepository).save(any(TenderTask.class));
        }

        @Test
        @DisplayName("processTask：重试耗尽后进入 DLQ 并写入 dlq 表")
        void processTask_retriesExhausted_movesToDlq() {
            TenderTask task = makeTask(105L, "worker-1");
            task.setAttempts(3);
            when(tenderTaskRepository.findById(105L)).thenReturn(Optional.of(task));
            when(storageGuardService.resolveAndValidate(anyString())).thenReturn(Path.of("/tmp/test.pdf"));
            doNothing().when(storageGuardService).ensureExists(any());
            when(storageGuardService.fileSize(any())).thenReturn(1000L);
            when(storageGuardService.sha256(any())).thenThrow(new RuntimeException("sidecar unavailable"));
            when(failureClassifier.classify(any())).thenReturn(
                    com.xiyu.bid.platform.async.domain.AsyncFailureKind.TRANSIENT_DEPENDENCY);

            workerService.processTask(105L, "worker-1");

            ArgumentCaptor<TenderTask> taskCaptor = ArgumentCaptor.forClass(TenderTask.class);
            verify(tenderTaskRepository).save(taskCaptor.capture());
            assertThat(taskCaptor.getValue().getStatus()).isEqualTo(TenderTaskStatus.DLQ);

            ArgumentCaptor<TenderTaskDlq> dlqCaptor = ArgumentCaptor.forClass(TenderTaskDlq.class);
            verify(tenderTaskDlqRepository).save(dlqCaptor.capture());
            assertThat(dlqCaptor.getValue().getTaskId()).isEqualTo(105L);
            assertThat(dlqCaptor.getValue().getErrorCode()).isNotBlank();
        }
    }

    // ========== 资源保护边界测试 ==========

    @Nested
    @DisplayName("资源保护边界")
    class ResourceProtection {

        @Test
        @DisplayName("claimRunnableTasks：limit <= 0 时直接返回空，不尝试获取锁")
        void claimRunnableTasks_zeroLimit_noLockAttempt() {
            List<TenderTask> result = workerService.claimRunnableTasks("worker-x", 0);
            assertThat(result).isEmpty();
            verify(claimLock, never()).tryAcquire();
        }

        @Test
        @DisplayName("claimRunnableTasks：running 任务数达到上限时返回空")
        void claimRunnableTasks_concurrencyAtLimit_returnsEmpty() {
            when(claimLock.tryAcquire()).thenReturn(true);
            when(tenderTaskRepository.countByStatus(TenderTaskStatus.RUNNING)).thenReturn(4L);
            properties.setMaxGlobalConcurrency(4);

            List<TenderTask> result = workerService.claimRunnableTasks("worker-1", 10);

            assertThat(result).isEmpty();
        }
    }

    // ========== 去重冲突边界测试 ==========

    @Nested
    @DisplayName("去重冲突边界")
    class DedupEdgeCases {

        @Test
        @DisplayName("去重冲突时新文件被标记为 DUPLICATE，原始文件不受影响")
        void dedupConflict_marksNewFileDuplicate() {
            // 新文件（冲突方）
            TenderFile newFile = TenderFile.builder()
                    .id(2L).userId(1L).filePath("new.pdf").build();
            TenderTask task = TenderTask.builder()
                    .id(1L).file(newFile).status(TenderTaskStatus.RUNNING)
                    .lockedBy("worker-1").attempts(0)
                    .priority(5).availableAt(LocalDateTime.now())
                    .build();
            task.setLockedAt(LocalDateTime.now());

            when(tenderTaskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(storageGuardService.resolveAndValidate(anyString())).thenReturn(Path.of("/tmp/test"));
            doNothing().when(storageGuardService).ensureExists(any());
            when(storageGuardService.fileSize(any())).thenReturn(1000L);
            when(storageGuardService.sha256(any())).thenReturn("abc123");
            // 第一次 save 抛 DataIntegrityViolationException 触发去重
            when(tenderFileRepository.save(any(TenderFile.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"))
                    .thenAnswer(inv -> inv.getArgument(0));
            // 原始文件
            when(tenderFileRepository.findByUserIdAndFileSha256(eq(1L), eq("abc123")))
                    .thenReturn(Optional.of(TenderFile.builder().id(1L).filePath("original.pdf").build()));

            workerService.processTask(1L, "worker-1");

            ArgumentCaptor<TenderFile> captor = ArgumentCaptor.forClass(TenderFile.class);
            verify(tenderFileRepository, atLeast(1)).save(captor.capture());
            assertThat(captor.getAllValues().stream()
                    .anyMatch(f -> f.getUploadStatus() == TenderFileUploadStatus.DUPLICATE))
                    .isTrue();
        }
    }
}
