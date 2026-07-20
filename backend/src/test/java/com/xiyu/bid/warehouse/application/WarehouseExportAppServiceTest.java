package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarehouseExportAppService 单元测试。
 *
 * <p>验证修复 @Async self-invocation + @Transactional 竞态后的行为：
 * <ol>
 *   <li>export() 委托 stateService.createTask 创建 PENDING 任务（独立事务，避免竞态）</li>
 *   <li>export() 委托调用 WarehouseExportAsyncExecutor（而非 this.executeExportAsync）</li>
 *   <li>exportByIds() 同理</li>
 * </ol>
 *
 * <p>根因：原代码 export() 内部直接调用 this.executeExportAsync()，
 * Spring AOP 代理不拦截 self-invocation，@Async 注解静默失效，
 * 导致 Word 合订本生成在 HTTP 线程同步执行超过 30 秒超时。
 *
 * <p>P1-1 修复：createTask 由 StateService 以独立事务执行并提交，
 * 异步线程可立即查询到，避免 @Async + @Transactional 竞态。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseExportAppServiceTest {

    @Mock
    private WarehouseExportTaskRepository exportTaskRepo;

    @Mock
    private WarehouseExportAsyncExecutor asyncExecutor;

    @Mock
    private WarehouseExportTaskStateService stateService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WarehouseExportAppService appService;

    @TempDir
    Path tempDir;

    @Test
    void export_shouldDelegateCreateTaskAndTriggerAsyncExecutor() {
        // Given
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        Long operatorId = 100L;
        String operatorUsername = "bid_admin";
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        when(stateService.createTask(any(), eq(operatorId))).thenReturn(1L);

        // When
        WarehouseExportAppService.ExportTaskResult result = appService.export(
                filterDTO, operatorId, operatorUsername, scope, forms);

        // Then: 返回 taskId
        assertThat(result.taskId()).isEqualTo(1L);

        // Then: 委托 stateService.createTask（独立事务，避免 @Async + @Transactional 竞态）
        verify(stateService, times(1)).createTask(any(), eq(operatorId));

        // Then: 委托调用 asyncExecutor.executeExport（而非 self-invocation）
        verify(asyncExecutor, times(1)).executeExport(
                eq(1L), eq(filterDTO), eq(operatorId), eq(operatorUsername),
                eq(scope), eq(forms), anyLong());
    }

    @Test
    void exportByIds_shouldDelegateCreateTaskAndTriggerAsyncExecutor() {
        // Given
        List<Long> ids = List.of(10L, 20L, 30L);
        Long operatorId = 100L;
        String operatorUsername = "bid_admin";
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        when(stateService.createTask(any(), eq(operatorId))).thenReturn(2L);

        // When
        WarehouseExportAppService.ExportTaskResult result = appService.exportByIds(
                ids, operatorId, operatorUsername, scope, forms);

        // Then: 返回 taskId
        assertThat(result.taskId()).isEqualTo(2L);

        // Then: 委托 stateService.createTask
        verify(stateService, times(1)).createTask(any(), eq(operatorId));

        // Then: 委托调用 asyncExecutor.executeExportByIds（而非 self-invocation）
        verify(asyncExecutor, times(1)).executeExportByIds(
                eq(2L), eq(ids), eq(operatorId), eq(operatorUsername),
                eq(scope), eq(forms), anyLong());
    }

    // ========== getExportFile 边界测试（流式下载 OOM 修复配套验证） ==========

    @Test
    void getExportFile_taskNotFound_throwsIllegalArgumentException() {
        Long taskId = 999L;
        Long userId = 1L;
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.getExportFile(taskId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("导出任务不存在或无权限");
    }

    @Test
    void getExportFile_statusNotCompleted_throwsIllegalStateException() {
        Long taskId = 1L;
        Long userId = 1L;
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(taskId)
                .status(WarehouseExportTaskEntity.ExportStatus.PROCESSING)
                .build();
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(taskId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出任务尚未完成");
    }

    @Test
    void getExportFile_fileExpired_throwsIllegalStateException() {
        Long taskId = 1L;
        Long userId = 1L;
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(taskId)
                .status(WarehouseExportTaskEntity.ExportStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .storedFilePath("/tmp/test.zip")
                .build();
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(taskId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出文件已过期");
    }

    @Test
    void getExportFile_storedFilePathNull_throwsIllegalStateException() {
        Long taskId = 1L;
        Long userId = 1L;
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(taskId)
                .status(WarehouseExportTaskEntity.ExportStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .storedFilePath(null)
                .build();
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(taskId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出文件路径为空");
    }

    @Test
    void getExportFile_fileNotExistsOnDisk_throwsIllegalStateException() throws IOException {
        Long taskId = 1L;
        Long userId = 1L;
        Path nonExistent = tempDir.resolve("nonexistent.zip");
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(taskId)
                .status(WarehouseExportTaskEntity.ExportStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .storedFilePath(nonExistent.toString())
                .build();
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appService.getExportFile(taskId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出文件已被清理");
    }

    @Test
    void getExportFile_validCompletedTask_returnsPath() throws IOException {
        Long taskId = 1L;
        Long userId = 1L;
        Path realFile = tempDir.resolve("export.zip");
        Files.write(realFile, new byte[]{1, 2, 3, 4});

        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(taskId)
                .status(WarehouseExportTaskEntity.ExportStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .storedFilePath(realFile.toString())
                .build();
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.of(task));

        Path result = appService.getExportFile(taskId, userId);

        assertThat(result).isEqualTo(realFile);
        assertThat(Files.exists(result)).isTrue();
    }

    @Test
    void getExportFile_expiresAtNull_doesNotThrowExpired() throws IOException {
        Long taskId = 1L;
        Long userId = 1L;
        Path realFile = tempDir.resolve("export.zip");
        Files.write(realFile, new byte[]{1, 2, 3});

        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(taskId)
                .status(WarehouseExportTaskEntity.ExportStatus.COMPLETED)
                .expiresAt(null)
                .storedFilePath(realFile.toString())
                .build();
        when(exportTaskRepo.findByIdAndCreatedBy(taskId, userId)).thenReturn(Optional.of(task));

        Path result = appService.getExportFile(taskId, userId);
        assertThat(result).isEqualTo(realFile);
    }
}
