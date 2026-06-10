package com.xiyu.bid.personnel.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelZipExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExportPersonnelAppService 单元测试
 * 验证人员导出进度管理及文件下载链路。
 */
class ExportPersonnelAppServiceTest {

    @TempDir
    Path tempDir;

    private PersonnelRepository repository;
    private PersonnelZipExporter zipExporter;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private PersonnelOperationLogService logService;
    private ExportPersonnelAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(PersonnelRepository.class);
        zipExporter = mock(PersonnelZipExporter.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        logService = mock(PersonnelOperationLogService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ExportPersonnelAppService(
                repository,
                zipExporter,
                redisTemplate,
                objectMapper,
                logService
        );
    }

    @Test
    void shouldInitiateExportTask() {
        var info = service.initiateExportTask(1L, "测试操作人");

        assertThat(info.taskId()).isNotBlank();
        assertThat(info.taskNo()).isNotBlank();
    }

    @Test
    void shouldReturnNotFoundWhenTaskDoesNotExist() {
        when(valueOps.get(contains("progress"))).thenReturn(null);
        when(valueOps.get(contains("file"))).thenReturn(null);

        ExportPersonnelAppService.ExportProgress progress = service.getProgress("non-existent-task");

        assertThat(progress.status()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldReturnCompletedWhenFileExistsOnDisk() throws IOException {
        Path exportDir = tempDir.resolve("data/personnel-exports");
        Files.createDirectories(exportDir);
        Path zipFile = exportDir.resolve("personnel_export_test-task_123.zip");
        Files.writeString(zipFile, "PK"); // minimal ZIP-like content

        when(valueOps.get(contains("progress"))).thenReturn(null);
        when(valueOps.get(contains("file"))).thenReturn(zipFile.toString());

        ExportPersonnelAppService.ExportProgress progress = service.getProgress("test-task");

        assertThat(progress.status()).isEqualTo("COMPLETED");
        assertThat(progress.downloadPath()).isEqualTo(zipFile.toString());
    }

    @Test
    void shouldThrowWhenExportFileNotFound() {
        when(valueOps.get(contains("file"))).thenReturn(null);

        assertThatThrownBy(() -> service.getExportFile("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在或已过期");
    }

    @Test
    void shouldUpdateProgressWithRedis() {
        service.updateProgress("task-1", "正在查询数据...", 20);

        verify(valueOps).set(
                eq("personnel:export:progress:task-1"),
                contains("PROCESSING"),
                any()
        );
    }

    @Test
    void shouldStoreOperatorInfo() {
        service.initiateExportTask(99L, "王五");

        verify(valueOps, atLeastOnce()).set(
                contains("personnel:export:operator:"),
                contains("王五"),
                any()
        );
    }
}
