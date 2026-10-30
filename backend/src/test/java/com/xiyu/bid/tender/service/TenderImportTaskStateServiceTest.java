package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenderImportTaskStateServiceTest {

    @Mock
    private TenderImportTaskRepository taskRepository;
    @Mock
    private TenderImportProgressService progressService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TenderImportTaskStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new TenderImportTaskStateService(taskRepository, objectMapper, progressService);
    }

    @Test
    @DisplayName("createTask: 创建初始任务，计数全为 0，状态为 PENDING")
    void createTask_initializesCorrectly() {
        when(taskRepository.save(any(TenderImportTask.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenderImportTask task = stateService.createTask("task-1", 100L, "test.xlsx");

        assertThat(task.getTaskId()).isEqualTo("task-1");
        assertThat(task.getUserId()).isEqualTo(100L);
        assertThat(task.getFileName()).isEqualTo("test.xlsx");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getTotalRows()).isEqualTo(0);
        assertThat(task.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("markFailed: 四参数重载正确回写 totalRows 和 failureCount")
    void markFailed_withCounts_persistsMetrics() {
        String taskId = "task-fail-metrics";
        TenderImportTask existing = TenderImportTask.builder()
                .taskId(taskId)
                .totalRows(0)
                .failureCount(0)
                .status("PROCESSING")
                .build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        List<TenderImportTaskError> errors = List.of(
                new TenderImportTaskError(2, "customerType", "客户类型不能为空", null),
                new TenderImportTaskError(2, "priority", "优先级不能为空", null)
        );

        stateService.markFailed(taskId, 5, 2, errors);

        ArgumentCaptor<TenderImportTask> captor = ArgumentCaptor.forClass(TenderImportTask.class);
        verify(taskRepository).save(captor.capture());
        TenderImportTask saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getTotalRows()).isEqualTo(5);
        assertThat(saved.getProcessedRows()).isEqualTo(5);
        assertThat(saved.getSuccessCount()).isEqualTo(0);
        assertThat(saved.getFailureCount()).isEqualTo(2);
        assertThat(saved.getErrorDetails()).contains("客户类型不能为空");
        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed: 两参数重载兼容未知行数场景，自动从 errors 回退计数")
    void markFailed_fallbackCountsFromErrors() {
        String taskId = "task-fallback";
        TenderImportTask existing = TenderImportTask.builder()
                .taskId(taskId)
                .totalRows(0)
                .failureCount(0)
                .status("PROCESSING")
                .build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        List<TenderImportTaskError> errors = List.of(
                new TenderImportTaskError(3, "title", "标题不能为空", null)
        );

        stateService.markFailed(taskId, errors);

        ArgumentCaptor<TenderImportTask> captor = ArgumentCaptor.forClass(TenderImportTask.class);
        verify(taskRepository).save(captor.capture());
        TenderImportTask saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getTotalRows()).isEqualTo(1);
        assertThat(saved.getFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("markCompleted: 正确更新总行数与 COMPLETED 状态")
    void markCompleted_updatesStatusAndCounts() {
        String taskId = "task-completed";
        TenderImportTask existing = TenderImportTask.builder()
                .taskId(taskId)
                .totalRows(0)
                .status("PROCESSING")
                .build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        stateService.markCompleted(taskId, 10);

        ArgumentCaptor<TenderImportTask> captor = ArgumentCaptor.forClass(TenderImportTask.class);
        verify(taskRepository).save(captor.capture());
        TenderImportTask saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getTotalRows()).isEqualTo(10);
        assertThat(saved.getSuccessCount()).isEqualTo(10);
        assertThat(saved.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("markPartialSuccess: 正确更新成功与失败计数")
    void markPartialSuccess_updatesCounts() {
        String taskId = "task-partial";
        TenderImportTask existing = TenderImportTask.builder()
                .taskId(taskId)
                .totalRows(0)
                .status("PROCESSING")
                .build();
        when(taskRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        List<TenderImportTaskError> errors = List.of(
                new TenderImportTaskError(4, "duplicate", "重复", "标讯A")
        );

        stateService.markPartialSuccess(taskId, 10, 8, 2, errors);

        ArgumentCaptor<TenderImportTask> captor = ArgumentCaptor.forClass(TenderImportTask.class);
        verify(taskRepository).save(captor.capture());
        TenderImportTask saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(saved.getTotalRows()).isEqualTo(10);
        assertThat(saved.getSuccessCount()).isEqualTo(8);
        assertThat(saved.getFailureCount()).isEqualTo(2);
    }
}
