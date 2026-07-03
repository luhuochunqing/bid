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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
        MultipartFile file = new MockMultipartFile("test.xlsx", new byte[]{});
        when(excelImporter.importFromStream(any())).thenThrow(new RuntimeException("Excel 解析异常"));

        PersonnelImportTask task = buildTask(100L, "IMP-PER-TEST-001", ImportTaskStatus.PENDING);
        when(importTaskRepository.findById(100L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeImportAsync(100L, file, 1L);

        verify(importTaskRepository).save(argThat(saved ->
                saved.status().name().equals("FAILED")
        ));
    }

    @Test
    void executeImportAsync_当excelImporter抛IOException_应调用failImportTask() throws Exception {
        MultipartFile file = new MockMultipartFile("test.xlsx", new byte[]{});
        when(excelImporter.importFromStream(any())).thenThrow(new java.io.IOException("文件读取失败"));

        PersonnelImportTask task = buildTask(101L, "IMP-PER-TEST-002", ImportTaskStatus.PENDING);
        when(importTaskRepository.findById(101L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeImportAsync(101L, file, 1L);

        verify(importTaskRepository).save(argThat(saved ->
                saved.status().name().equals("FAILED")
        ));
    }

    @Test
    void executeImportAsync_当importExecutor抛RuntimeException_应调用failImportTask() throws Exception {
        MultipartFile file = new MockMultipartFile("test.xlsx", new byte[]{});

        PersonnelExcelImporter.ImportResult importResult = new PersonnelExcelImporter.ImportResult(
                List.of(), List.of(), List.of(),
                ValidationResult.empty()
        );
        when(excelImporter.importFromStream(any())).thenReturn(importResult);

        when(importExecutor.executeImport(any(), any())).thenThrow(new IllegalStateException("导入执行异常"));

        PersonnelImportTask task = buildTask(103L, "IMP-PER-TEST-004", ImportTaskStatus.PENDING);
        when(importTaskRepository.findById(103L)).thenReturn(Optional.of(task));
        when(importTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeImportAsync(103L, file, 1L);

        verify(importTaskRepository).save(argThat(saved ->
                saved.status().name().equals("FAILED")
        ));
    }
}
