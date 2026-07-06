package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
import com.xiyu.bid.personnel.domain.model.importtask.ImportTaskStatus;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.domain.port.PersonnelImportTaskRepository;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelExcelImporter;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportErrorReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ImportPersonnelAppServiceTest {

    private PersonnelExcelImporter excelImporter;
    private PersonnelImportExecutor importExecutor;
    private PersonnelImportTaskRepository importTaskRepository;
    private PersonnelImportErrorReportGenerator errorReportGenerator;
    private PersonnelOperationLogService operationLogService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private ImportPersonnelAppService service;

    @BeforeEach
    void setUp() {
        excelImporter = mock(PersonnelExcelImporter.class);
        importExecutor = mock(PersonnelImportExecutor.class);
        importTaskRepository = mock(PersonnelImportTaskRepository.class);
        errorReportGenerator = mock(PersonnelImportErrorReportGenerator.class);
        operationLogService = mock(PersonnelOperationLogService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        PersonnelImportProgressService progressService = new PersonnelImportProgressService(
                Optional.of(redisTemplate),
                objectMapper,
                importTaskRepository,
                errorReportGenerator
        );

        service = new ImportPersonnelAppService(
                excelImporter,
                importExecutor,
                progressService,
                importTaskRepository,
                errorReportGenerator,
                operationLogService
        );
    }

    @Test
    void shouldInitiateImportTask() {
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PersonnelImportTask task = service.initiateImportTask(1L, "测试操作人");

        assertThat(task).isNotNull();
        assertThat(task.taskNo()).startsWith("IMP-PER-");
    }

    private PersonnelImportTask buildTask(Long id, String taskNo, ImportTaskStatus status) {
        return new PersonnelImportTask(
                id, taskNo, "PERSONNEL", status,
                0, 0, 0, 0,
                List.of(), null, 1L,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void executeImportAsync_当excelImporter抛RuntimeException_应调用failImportTask() throws Exception {
        byte[] fileBytes = new byte[]{};
        when(excelImporter.importFromStream(any())).thenThrow(new RuntimeException("Excel 解析异常"));

        PersonnelImportTask task = buildTask(100L, "IMP-PER-TEST-001", ImportTaskStatus.PENDING);
        when(importTaskRepository.findById(100L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeImportAsync(100L, fileBytes, "test.xlsx", 1L);

        verify(importTaskRepository).save(argThat(saved ->
                saved.status().name().equals("FAILED")
        ));
    }

    @Test
    void executeImportAsync_当excelImporter抛IOException_应调用failImportTask() throws Exception {
        byte[] fileBytes = new byte[]{};
        when(excelImporter.importFromStream(any())).thenThrow(new java.io.IOException("文件读取失败"));

        PersonnelImportTask task = buildTask(101L, "IMP-PER-TEST-002", ImportTaskStatus.PENDING);
        when(importTaskRepository.findById(101L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeImportAsync(101L, fileBytes, "test.xlsx", 1L);

        verify(importTaskRepository).save(argThat(saved ->
                saved.status().name().equals("FAILED")
        ));
    }

    @Test
    void executeImportAsync_当importExecutor抛RuntimeException_应调用failImportTask() throws Exception {
        byte[] fileBytes = new byte[]{};

        PersonnelExcelImporter.ImportResult importResult = new PersonnelExcelImporter.ImportResult(
                List.of(), List.of(), List.of(),
                ValidationResult.empty()
        );
        when(excelImporter.importFromStream(any())).thenReturn(importResult);

        when(importExecutor.executeImport(any(), any())).thenThrow(new IllegalStateException("导入执行异常"));

        PersonnelImportTask task = buildTask(103L, "IMP-PER-TEST-004", ImportTaskStatus.PENDING);
        when(importTaskRepository.findById(103L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeImportAsync(103L, fileBytes, "test.xlsx", 1L);

        verify(importTaskRepository).save(argThat(saved ->
                saved.status().name().equals("FAILED")
        ));
    }

    /**
     * CO-469 第八轮防复发测试：
     * 验证 failImportTask 自身 save 失败时（如 DataIntegrityViolationException），会降级到 updateStatus(FAILED)，
     * 而不是抛二次异常被 SimpleAsyncUncaughtExceptionHandler 吞掉，导致任务状态永久停在 PROCESSING/5%。
     */
    @Test
    void executeImportAsync_当failImportTask自身save抛异常_应降级到updateStatus() throws Exception {
        byte[] fileBytes = new byte[]{};
        when(excelImporter.importFromStream(any())).thenThrow(new RuntimeException("Excel 解析异常"));

        PersonnelImportTask task = buildTask(200L, "IMP-PER-TEST-CO469-8", ImportTaskStatus.PROCESSING);
        when(importTaskRepository.findById(200L)).thenReturn(Optional.of(task));
        // 第一次 save（failImportTask 内）抛 DataIntegrityViolationException 模拟 JSON cast 失败
        when(importTaskRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "could not execute statement [Data truncation: Invalid JSON text]"));

        service.executeImportAsync(200L, fileBytes, "test.xlsx", 1L);

        // 断言：降级到 updateStatus(200L, "FAILED")
        verify(importTaskRepository).updateStatus(eq(200L), eq("FAILED"));
    }

    /**
     * CO-469 第八轮防复发测试：
     * 验证 failImportTask save 和 updateStatus 都失败时，service.executeImportAsync 仍不抛异常，
     * 且会调用 clearProgress（通过 Redis 进度被清理验证），避免前端继续轮询。
     */
    @Test
    void executeImportAsync_当failImportTask完全失败时_不抛异常且清理Redis进度() throws Exception {
        byte[] fileBytes = new byte[]{};
        when(excelImporter.importFromStream(any())).thenThrow(new RuntimeException("解析异常"));

        PersonnelImportTask task = buildTask(201L, "IMP-PER-TEST-CO469-8-FALLBACK", ImportTaskStatus.PROCESSING);
        when(importTaskRepository.findById(201L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Invalid JSON text"));
        // updateStatus 也失败
        when(importTaskRepository.updateStatus(eq(201L), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("updateStatus also failed"));

        // 不应抛异常
        service.executeImportAsync(201L, fileBytes, "test.xlsx", 1L);

        // 验证 save 和 updateStatus 都被尝试过
        verify(importTaskRepository).save(any());
        verify(importTaskRepository).updateStatus(eq(201L), eq("FAILED"));
        // 验证 Redis 进度被清理（PersonnelImportProgressService.clearProgress 会调用 template.delete）
        verify(redisTemplate).delete("personnel:import:progress:201");
    }
}
