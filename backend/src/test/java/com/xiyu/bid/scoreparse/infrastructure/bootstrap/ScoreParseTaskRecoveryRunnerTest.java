package com.xiyu.bid.scoreparse.infrastructure.bootstrap;

import com.xiyu.bid.scoreparse.application.ScoreParseProgressService;
import com.xiyu.bid.scoreparse.application.ScoreParseTaskStateService;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务启动卡死任务恢复 Runner 测试（spec 041 US5；spec 045 US2 临时文件清理）。
 * <p>启动时扫描 PROCESSING 超阈值任务 → failTask 三层降级 + 清 Redis + 释放临时文件；
 * 参考 TenderImportTaskRecoveryRunner（spec 031）范式。
 */
@ExtendWith(MockitoExtension.class)
class ScoreParseTaskRecoveryRunnerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 15, 12, 0, 0);

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreParseTaskStateService stateService;
    @Mock
    private ScoreParseProgressService progressService;
    @Mock
    private TempBidFileStore tempFileStore;

    private ScoreParseTaskRecoveryRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ScoreParseTaskRecoveryRunner(
                taskRepository, stateService, progressService, tempFileStore, 30);
    }

    @Test
    void run_callsCleanupAllAndRecovers() {
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(tempFileStore).cleanupAll();
    }

    @Test
    void stuckProcessingTask_recoveredWithFailTaskAndProgressClearedAndFileReleased() {
        ScoreParseTask stuck = task("task-stuck", "PARSE");
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of(stuck));

        int recovered = runner.recover(NOW);

        assertThat(recovered).isEqualTo(1);
        verify(stateService).failTask(eq("task-stuck"), contains("服务重启"));
        verify(progressService).clearProgress("task-stuck");
        verify(tempFileStore).release("task-stuck");
    }

    @Test
    void thresholdComputedAsNowMinusConfiguredMinutes() {
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        runner.recover(NOW);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskRepository).findByStatusAndUpdatedAtBefore(eq("PROCESSING"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(NOW.minusMinutes(30));
    }

    @Test
    void singleTaskFailure_continuesRemainingTasks() {
        ScoreParseTask first = task("task-a", "PARSE");
        ScoreParseTask second = task("task-b", "SCORING");
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("DB down"))
                .when(stateService).failTask(eq("task-a"), anyString());

        when(taskRepository.findByTaskTypeAndStatusInAndCreatedAtBefore("SCORING",List.of("PROCESSING"),NOW.minusSeconds(1800)))
                .thenReturn(List.of(second));
        int recovered = runner.recover(NOW);

        assertThat(recovered).isEqualTo(1);
        verify(stateService).failTask(eq("task-b"), anyString());
        verify(progressService).clearProgress("task-b");
    }

    @Test
    void noStuckTasks_noop() {
        when(taskRepository.findByStatusAndUpdatedAtBefore(eq("PROCESSING"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int recovered = runner.recover(NOW);

        assertThat(recovered).isZero();
        verify(stateService, never()).failTask(anyString(), anyString());
        verify(progressService, never()).clearProgress(anyString());
    }

    private ScoreParseTask task(String taskId, String taskType) {
        return ScoreParseTask.builder()
                .id(1L)
                .taskId(taskId)
                .projectId(1L)
                .taskType(taskType)
                .status("PROCESSING")
                .build();
    }
}
