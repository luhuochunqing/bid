// Input: mock 任务仓库/状态机/进度缓存 + 注入时钟
// Output: 超时扫描断言（FAILED+timeout_marked、阈值计算、失败续扫）
// Pos: scoreparse/infrastructure/scheduler — spec 041 US5（FR-020）
package com.xiyu.bid.scoreparse.infrastructure.scheduler;

import com.xiyu.bid.scoreparse.application.ScoreParseProgressService;
import com.xiyu.bid.scoreparse.application.ScoreParseTaskStateService;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 30 分钟超时扫描 Job 测试（spec 041 US5，FR-020）。
 * <p>PROCESSING 超时 → FAILED + timeout_marked=1（契约 errorMessage）；
 * 阈值 app.score-parse.timeout-minutes 可注入；单任务失败不中断扫描。
 */
@ExtendWith(MockitoExtension.class)
class ScoreParseTimeoutScanJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 15, 12, 0, 0);

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreParseTaskStateService stateService;
    @Mock
    private ScoreParseProgressService progressService;

    private ScoreParseTimeoutScanJob job;

    @BeforeEach
    void setUp() {
        job = new ScoreParseTimeoutScanJob(
                taskRepository, stateService, progressService, 30);
    }

    @Test
    void processingBeyondTimeout_markedTimeoutAndProgressCleared() {
        ScoreParseTask stuck = task("task-stuck");
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of(stuck));

        int marked = job.processScan(NOW);

        assertThat(marked).isEqualTo(1);
        verify(stateService).markTimeout("task-stuck");
        verify(progressService).clearProgress("task-stuck");
    }

    @Test
    void thresholdComputedAsNowMinusConfiguredMinutes() {
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        job.processScan(NOW);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskRepository).findByStatusAndUpdatedAtBefore(eq("PROCESSING"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(NOW.minusMinutes(30));
    }

    @Test
    void customTimeoutMinutes_respected() {
        ScoreParseTimeoutScanJob tenMinuteJob = new ScoreParseTimeoutScanJob(
                taskRepository, stateService, progressService, 10);
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        tenMinuteJob.processScan(NOW);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskRepository).findByStatusAndUpdatedAtBefore(eq("PROCESSING"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(NOW.minusMinutes(10));
    }

    @Test
    void markTimeoutFailure_continuesRemainingTasks() {
        ScoreParseTask first = task("task-a");
        ScoreParseTask second = task("task-b");
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("DB down"))
                .when(stateService).markTimeout("task-a");

        int marked = job.processScan(NOW);

        assertThat(marked).isEqualTo(1);
        verify(stateService).markTimeout("task-b");
        verify(progressService).clearProgress("task-b");
    }

    @Test
    void noTimedOutTasks_noop() {
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int marked = job.processScan(NOW);

        assertThat(marked).isZero();
        verify(stateService, never()).markTimeout(anyString());
        verify(progressService, never()).clearProgress(anyString());
    }

    private ScoreParseTask task(String taskId) {
        return ScoreParseTask.builder()
                .id(1L)
                .taskId(taskId)
                .projectId(1L)
                .taskType("PARSE")
                .status("PROCESSING")
                .build();
    }
}
