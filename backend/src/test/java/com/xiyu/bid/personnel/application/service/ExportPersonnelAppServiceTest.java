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
import java.util.List;
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
                Optional.of(redisTemplate),
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

    /**
     * CO-469 第三轮：防复发测试
     * 原 bug：zipExporter.exportZip 抛 NPE 时，catch (IOException) 接不住，
     *        异步线程静默终止，进度永久卡在 70%
     * 修复：catch (Exception) 接住所有异常，调用 failExportTask 写入 FAILED 状态
     */
    @Test
    void executeExportAsync_当zipExporter抛NPE_应调用failExportTask并写入FAILED状态() throws Exception {
        // Arrange: repository 返回非空 list，确保走到 zipExporter.exportZip()
        when(repository.findAll(any())).thenReturn(List.of(
                new com.xiyu.bid.personnel.domain.model.Personnel(
                        1L, "张三", "EMP001", "DEPT01", "技术部", "男",
                        null, null, "13800000000", "本科", "工程师",
                        com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus.ACTIVE,
                        null, null, null, null, null, null
                )
        ));
        // zipExporter 抛 NPE（模拟 Collectors.toMap null key 场景）
        when(zipExporter.exportZip(anyList())).thenThrow(new NullPointerException("Cannot invoke method on null"));

        // Act: 使用全 null 的 criteria（13 个字段）
        PersonnelListCriteria criteria = new PersonnelListCriteria(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        service.executeExportAsync("task-npe", criteria, 1L);

        // Assert: failExportTask 应被调用，写入 FAILED 状态
        verify(valueOps).set(
                eq("personnel:export:progress:task-npe"),
                contains("FAILED"),
                any()
        );
    }

    /**
     * CO-469 第三轮：防复发测试
     * 验证非 IOException 的 RuntimeException 也能被 catch 接住
     */
    @Test
    void executeExportAsync_当zipExporter抛RuntimeException_应调用failExportTask() throws Exception {
        // Arrange: repository 返回非空 list
        when(repository.findAll(any())).thenReturn(List.of(
                new com.xiyu.bid.personnel.domain.model.Personnel(
                        1L, "李四", "EMP002", "DEPT01", "技术部", "女",
                        null, null, "13900000000", "硕士", "高工",
                        com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus.ACTIVE,
                        null, null, null, null, null, null
                )
        ));
        when(zipExporter.exportZip(anyList())).thenThrow(new IllegalStateException("非法状态"));

        PersonnelListCriteria criteria = new PersonnelListCriteria(
                null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        service.executeExportAsync("task-runtime", criteria, 1L);

        verify(valueOps).set(
                eq("personnel:export:progress:task-runtime"),
                contains("FAILED"),
                any()
        );
    }
}
