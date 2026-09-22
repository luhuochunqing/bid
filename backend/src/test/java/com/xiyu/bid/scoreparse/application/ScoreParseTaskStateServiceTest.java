package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreParseTaskStateServiceTest {

    @Mock
    private ScoreParseTaskRepository taskRepository;

    @Mock
    private ScoreParseProgressService progressService;

    private ScoreParseTaskStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new ScoreParseTaskStateService(taskRepository, progressService);
    }

    @Test
    void createTask_locksProjectBeforeInsertingTask() {
        stateService.createTask("new-parse", 100L, "PARSE", null, null, "MANUAL");

        var ordered = inOrder(taskRepository);
        ordered.verify(taskRepository).lockProjectForTaskChange(100L);
        ordered.verify(taskRepository).save(any(ScoreParseTask.class));
    }

    @Test
    void createScoringTask_doesNotWaitForParseAdoptionLock() {
        stateService.createTask("new-scoring", 100L, "SCORING", null, null, "MANUAL");

        verify(taskRepository, never()).lockProjectForTaskChange(100L);
        verify(taskRepository).save(any(ScoreParseTask.class));
    }

    @Test
    @DisplayName("failTask: 层 1 标记成功时，必须清理 Redis 进度缓存")
    void failTask_layer1Success_mustClearRedisProgress() {
        String taskId = "task-layer1-ok";
        ScoreParseTask task = ScoreParseTask.builder()
                .taskId(taskId)
                .status("PROCESSING")
                .build();

        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScoreParseTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        stateService.failTask(taskId, "层 1 失败原因");

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorMessage()).isEqualTo("层 1 失败原因");
        assertThat(task.getCompletedAt()).isNotNull();

        verify(progressService, times(1)).clearProgress(taskId);
    }

    @Test
    @DisplayName("failTask: 层 1 抛异常而层 2 仅更新状态成功时，仍必须清理 Redis 进度缓存")
    void failTask_layer1Fails_layer2Succeeds_mustClearRedisProgress() {
        String taskId = "task-layer2-ok";
        ScoreParseTask task = ScoreParseTask.builder()
                .taskId(taskId)
                .status("PROCESSING")
                .build();

        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));
        // 第一次调用 save（层 1）抛异常，第二次调用 save（层 2）成功
        when(taskRepository.save(any(ScoreParseTask.class)))
                .thenThrow(new RuntimeException("DB full write error"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        stateService.failTask(taskId, "详细错误");

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getCompletedAt()).isNotNull();

        verify(progressService, times(1)).clearProgress(taskId);
    }

    @Test
    @DisplayName("failTask: 层 1 与层 2 均异常降级至层 3 时，依然保证清理 Redis 进度缓存")
    void failTask_allLayersFail_mustStillClearRedisProgress() {
        String taskId = "task-layer3-all-fail";
        when(taskRepository.findByTaskId(taskId)).thenThrow(new RuntimeException("DB unavailable"));

        stateService.failTask(taskId, "数据库彻底挂掉");

        verify(progressService, times(1)).clearProgress(taskId);
    }

    @Test
    @DisplayName("failTask: progressService.clearProgress 自身抛异常时不向上冒泡中断业务")
    void failTask_redisClearThrows_shouldNotBubbleException() {
        String taskId = "task-redis-down";
        ScoreParseTask task = ScoreParseTask.builder()
                .taskId(taskId)
                .status("PROCESSING")
                .build();

        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScoreParseTask.class))).thenReturn(task);
        doThrow(new RuntimeException("Redis connection lost")).when(progressService).clearProgress(taskId);

        assertThatCode(() -> stateService.failTask(taskId, "普通失败"))
                .doesNotThrowAnyException();

        verify(progressService, times(1)).clearProgress(taskId);
    }

    @Test
    @DisplayName("markTimeout: 判定超时任务时必须设置超时标识并无条件清理 Redis 进度缓存")
    void markTimeout_shouldSetTimeoutStatusAndClearRedis() {
        String taskId = "task-timeout-parse";
        ScoreParseTask task = ScoreParseTask.builder()
                .taskId(taskId)
                .taskType("PARSE")
                .status("PROCESSING")
                .build();

        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(ScoreParseTask.class))).thenReturn(task);

        stateService.markTimeout(taskId);

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getTimeoutMarked()).isTrue();
        assertThat(task.getErrorMessage()).isEqualTo(ScoreParseTaskStateService.TIMEOUT_MESSAGE_PARSE);
        assertThat(task.getCompletedAt()).isNotNull();

        verify(progressService, times(1)).clearProgress(taskId);
    }

    @Test
    @DisplayName("markTimeout: taskRepository 操作异常时仍无条件触发 Redis 进度清理")
    void markTimeout_repositoryThrows_mustStillClearRedis() {
        String taskId = "task-timeout-db-error";
        when(taskRepository.findByTaskId(taskId)).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> stateService.markTimeout(taskId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        verify(progressService, times(1)).clearProgress(taskId);
    }
}
