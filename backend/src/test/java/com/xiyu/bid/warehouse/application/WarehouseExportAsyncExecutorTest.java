package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExcelWriter;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import com.xiyu.bid.warehouse.infrastructure.WarehouseWordBundleBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarehouseExportAsyncExecutor 单元测试。
 *
 * 验证从 AppService 提取的 @Async 方法行为：
 * 1. executeExport 流程：markProcessing → doExport → completeTask
 * 2. Word 合订本 buildBundle 失败时降级为 null，任务仍 COMPLETED（CO-582 §4 降级语义不回归）
 * 3. doExport 抛异常时调用 failTask，任务状态 FAILED
 *
 * 注意：findById 每次返回新对象实例，避免 ArgumentCaptor 捕获同一可变对象引用的陷阱。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseExportAsyncExecutorTest {

    @Mock
    private WarehouseExportTaskRepository exportTaskRepo;

    @Mock
    private WarehouseFilterService filterService;

    @Mock
    private WarehouseExcelWriter excelWriter;

    @Mock
    private WarehouseAttachmentRepository attachmentRepo;

    @Mock
    private WarehouseExportZipBuilder zipBuilder;

    @Mock
    private WarehouseWordBundleBuilder wordBundleBuilder;

    @Mock
    private WarehouseExportNotificationPublisher exportPublisher;

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path tempDir;

    @InjectMocks
    private WarehouseExportAsyncExecutor asyncExecutor;

    @Test
    void executeExport_shouldMarkProcessingThenCompleteTask() throws Exception {
        Long taskId = 1L;
        Long operatorId = 100L;
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        // findById 每次返回新对象，避免 ArgumentCaptor 可变对象陷阱
        when(exportTaskRepo.findById(taskId)).thenAnswer(inv ->
                Optional.of(WarehouseExportTaskEntity.builder()
                        .id(taskId).status(ExportStatus.PENDING).createdBy(operatorId).build()));

        WarehouseEntity entity = WarehouseEntity.builder().id(10L).createdBy(operatorId).build();
        when(filterService.filterAll(filterDTO)).thenReturn(List.of(entity));
        when(attachmentRepo.findByWarehouseIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(any())).thenReturn(List.of());
        when(excelWriter.write(any(), anyList())).thenReturn(new byte[]{1, 2, 3});
        when(wordBundleBuilder.buildBundle(anyList(), any())).thenReturn(new byte[]{4, 5, 6});

        // 创建真实的临时 zip 文件，避免 saveZip 的 Files.copy 失败
        Path zipFile = Files.createFile(tempDir.resolve("test.zip"));
        WarehouseExportZipBuilder.ZipBuildResult zipResult = new WarehouseExportZipBuilder.ZipBuildResult(
                zipFile, 100L, new WarehouseExportZipBuilder.ZipStats());
        when(zipBuilder.buildZip(any(), anyList(), any(), any(), eq(forms))).thenReturn(zipResult);
        when(exportPublisher.buildResultSummaryJson(anyInt(), any(), any(), anyLong(), any()))
                .thenReturn("{}");

        asyncExecutor.setExportRoot(tempDir.toString());

        // When
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务状态 PROCESSING → COMPLETED（3 次 save：markProcessing + completeTask + publish? 不，completeTask 内部 1 次 save）
        ArgumentCaptor<WarehouseExportTaskEntity> taskCaptor = ArgumentCaptor.forClass(WarehouseExportTaskEntity.class);
        verify(exportTaskRepo, times(2)).save(taskCaptor.capture());
        List<WarehouseExportTaskEntity> savedTasks = taskCaptor.getAllValues();
        assertThat(savedTasks.get(0).getStatus()).isEqualTo(ExportStatus.PROCESSING);
        assertThat(savedTasks.get(1).getStatus()).isEqualTo(ExportStatus.COMPLETED);
        assertThat(savedTasks.get(1).getStoredFilePath()).isNotNull();
        assertThat(savedTasks.get(1).getExpiresAt()).isNotNull();
        assertThat(savedTasks.get(1).getCompletedAt()).isNotNull();
    }

    @Test
    void executeExport_whenWordBundleFails_shouldDegradeAndStillComplete() throws Exception {
        Long taskId = 2L;
        Long operatorId = 100L;
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        when(exportTaskRepo.findById(taskId)).thenAnswer(inv ->
                Optional.of(WarehouseExportTaskEntity.builder()
                        .id(taskId).status(ExportStatus.PENDING).createdBy(operatorId).build()));

        WarehouseEntity entity = WarehouseEntity.builder().id(10L).createdBy(operatorId).build();
        when(filterService.filterAll(filterDTO)).thenReturn(List.of(entity));
        when(attachmentRepo.findByWarehouseIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(any())).thenReturn(List.of());
        when(excelWriter.write(any(), anyList())).thenReturn(new byte[]{1, 2, 3});
        // Word 合订本生成失败
        when(wordBundleBuilder.buildBundle(anyList(), any()))
                .thenThrow(new RuntimeException("PDF 渲染失败"));

        Path zipFile = Files.createFile(tempDir.resolve("test_degraded.zip"));
        WarehouseExportZipBuilder.ZipBuildResult zipResult = new WarehouseExportZipBuilder.ZipBuildResult(
                zipFile, 100L, new WarehouseExportZipBuilder.ZipStats());
        when(zipBuilder.buildZip(any(), anyList(), any(), eq(null), eq(forms))).thenReturn(zipResult);
        when(exportPublisher.buildResultSummaryJson(anyInt(), any(), any(), anyLong(), any()))
                .thenReturn("{}");

        asyncExecutor.setExportRoot(tempDir.toString());

        // When
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务仍 COMPLETED（降级语义，CO-582 §4）
        ArgumentCaptor<WarehouseExportTaskEntity> taskCaptor = ArgumentCaptor.forClass(WarehouseExportTaskEntity.class);
        verify(exportTaskRepo, times(2)).save(taskCaptor.capture());
        List<WarehouseExportTaskEntity> savedTasks = taskCaptor.getAllValues();
        assertThat(savedTasks.get(0).getStatus()).isEqualTo(ExportStatus.PROCESSING);
        assertThat(savedTasks.get(1).getStatus()).isEqualTo(ExportStatus.COMPLETED);
        // 验证 ZIP 构建时 wordBytes=null（降级）
        verify(zipBuilder).buildZip(any(), anyList(), any(), eq(null), eq(forms));
    }

    @Test
    void executeExport_whenFilterServiceThrows_shouldFailTask() {
        Long taskId = 3L;
        Long operatorId = 100L;
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        when(exportTaskRepo.findById(taskId)).thenAnswer(inv ->
                Optional.of(WarehouseExportTaskEntity.builder()
                        .id(taskId).status(ExportStatus.PENDING).createdBy(operatorId).build()));
        when(filterService.filterAll(filterDTO)).thenThrow(new RuntimeException("数据库连接失败"));

        // When
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务 FAILED（2 次 save：markProcessing + failTask）
        ArgumentCaptor<WarehouseExportTaskEntity> taskCaptor = ArgumentCaptor.forClass(WarehouseExportTaskEntity.class);
        verify(exportTaskRepo, times(2)).save(taskCaptor.capture());
        List<WarehouseExportTaskEntity> savedTasks = taskCaptor.getAllValues();
        assertThat(savedTasks.get(0).getStatus()).isEqualTo(ExportStatus.PROCESSING);
        assertThat(savedTasks.get(1).getStatus()).isEqualTo(ExportStatus.FAILED);
        assertThat(savedTasks.get(1).getFailureReason()).contains("数据库连接失败");
        assertThat(savedTasks.get(1).getCompletedAt()).isNotNull();
    }

    @Test
    void executeExportByIds_shouldMarkProcessingThenCompleteTask() throws Exception {
        Long taskId = 4L;
        Long operatorId = 100L;
        List<Long> ids = List.of(10L, 20L);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        when(exportTaskRepo.findById(taskId)).thenAnswer(inv ->
                Optional.of(WarehouseExportTaskEntity.builder()
                        .id(taskId).status(ExportStatus.PENDING).createdBy(operatorId).build()));

        WarehouseEntity entity = WarehouseEntity.builder().id(10L).createdBy(operatorId).build();
        when(filterService.findAllByIds(ids)).thenReturn(List.of(entity));
        when(attachmentRepo.findByWarehouseIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(any())).thenReturn(List.of());
        when(excelWriter.write(any(), anyList())).thenReturn(new byte[]{1, 2, 3});
        when(wordBundleBuilder.buildBundle(anyList(), any())).thenReturn(new byte[]{4, 5, 6});

        Path zipFile = Files.createFile(tempDir.resolve("test_ids.zip"));
        WarehouseExportZipBuilder.ZipBuildResult zipResult = new WarehouseExportZipBuilder.ZipBuildResult(
                zipFile, 100L, new WarehouseExportZipBuilder.ZipStats());
        when(zipBuilder.buildZip(any(), anyList(), any(), any(), eq(forms))).thenReturn(zipResult);
        when(exportPublisher.buildResultSummaryJson(anyInt(), any(), any(), anyLong(), any()))
                .thenReturn("{}");

        asyncExecutor.setExportRoot(tempDir.toString());

        // When
        asyncExecutor.executeExportByIds(taskId, ids, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务状态 PROCESSING → COMPLETED
        ArgumentCaptor<WarehouseExportTaskEntity> taskCaptor = ArgumentCaptor.forClass(WarehouseExportTaskEntity.class);
        verify(exportTaskRepo, times(2)).save(taskCaptor.capture());
        List<WarehouseExportTaskEntity> savedTasks = taskCaptor.getAllValues();
        assertThat(savedTasks.get(0).getStatus()).isEqualTo(ExportStatus.PROCESSING);
        assertThat(savedTasks.get(1).getStatus()).isEqualTo(ExportStatus.COMPLETED);
    }
}
