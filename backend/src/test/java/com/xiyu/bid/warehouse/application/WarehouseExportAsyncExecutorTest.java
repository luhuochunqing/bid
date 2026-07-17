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
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import com.xiyu.bid.warehouse.infrastructure.WarehouseWordBundleBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarehouseExportAsyncExecutor 单元测试。
 *
 * <p>验证从 AppService 提取的 @Async 方法行为：
 * <ol>
 *   <li>executeExport 流程：stateService.markProcessing → doExport → stateService.complete</li>
 *   <li>Word 合订本 buildBundle 失败时降级为 null，任务仍 COMPLETED（CO-582 §4 降级语义不回归）</li>
 *   <li>doExport 抛 RuntimeException 时调用 stateService.fail，任务状态 FAILED</li>
 *   <li>doExport 抛 Error 时调用 stateService.fail 并重新抛出（CO-469 第四轮教训）</li>
 * </ol>
 *
 * <p>P2-3 修复：测试用 ReflectionTestUtils 设置 exportRoot，不再在生产代码开包级 setter。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseExportAsyncExecutorTest {

    @Mock
    private WarehouseExportTaskStateService stateService;

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

    @BeforeEach
    void setUp() {
        // P2-3 修复：用 ReflectionTestUtils 设置 exportRoot，不再用包级 setter 污染生产代码
        ReflectionTestUtils.setField(asyncExecutor, "exportRoot", tempDir.toString());
    }

    @Test
    void executeExport_shouldMarkProcessingThenCompleteTask() throws Exception {
        Long taskId = 1L;
        Long operatorId = 100L;
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        WarehouseEntity entity = WarehouseEntity.builder().id(10L).createdBy(operatorId).build();
        when(filterService.filterAll(filterDTO)).thenReturn(List.of(entity));
        when(attachmentRepo.findByWarehouseIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(any())).thenReturn(List.of());
        when(excelWriter.write(any(), anyList())).thenReturn(new byte[]{1, 2, 3});
        doNothing().when(wordBundleBuilder).buildBundle(anyList(), any(), any());

        // 创建真实的临时 zip 文件，避免 saveZip 的 Files.move 失败
        Path zipFile = Files.createFile(tempDir.resolve("test.zip"));
        WarehouseExportZipBuilder.ZipBuildResult zipResult = new WarehouseExportZipBuilder.ZipBuildResult(
                zipFile, 100L, new WarehouseExportZipBuilder.ZipStats());
        when(zipBuilder.buildZip(any(), anyList(), any(), any(), eq(forms))).thenReturn(zipResult);
        when(exportPublisher.buildResultSummaryJson(anyInt(), any(), any(), anyLong(), any()))
                .thenReturn("{}");

        // StateService.complete 返回 task 对象（供 publish 使用）
        WarehouseExportTaskEntity completedTask = WarehouseExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(operatorId).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        // When
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: markProcessing 被调用
        verify(stateService, times(1)).markProcessing(taskId);
        // Then: complete 被调用（不是 fail）
        verify(stateService, times(1)).complete(any());
        verify(stateService, times(0)).fail(anyLong(), any());
        // Then: publish 被调用
        verify(exportPublisher, times(1)).publish(any(), anyInt(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void executeExport_whenWordBundleFails_shouldDegradeAndStillComplete() throws Exception {
        Long taskId = 2L;
        Long operatorId = 100L;
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        WarehouseEntity entity = WarehouseEntity.builder().id(10L).createdBy(operatorId).build();
        when(filterService.filterAll(filterDTO)).thenReturn(List.of(entity));
        when(attachmentRepo.findByWarehouseIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(any())).thenReturn(List.of());
        when(excelWriter.write(any(), anyList())).thenReturn(new byte[]{1, 2, 3});
        // Word 合订本生成失败
        doThrow(new RuntimeException("PDF 渲染失败"))
                .when(wordBundleBuilder).buildBundle(anyList(), any(), any());

        Path zipFile = Files.createFile(tempDir.resolve("test_degraded.zip"));
        WarehouseExportZipBuilder.ZipBuildResult zipResult = new WarehouseExportZipBuilder.ZipBuildResult(
                zipFile, 100L, new WarehouseExportZipBuilder.ZipStats());
        when(zipBuilder.buildZip(any(), anyList(), any(), eq(null), eq(forms))).thenReturn(zipResult);
        when(exportPublisher.buildResultSummaryJson(anyInt(), any(), any(), anyLong(), any()))
                .thenReturn("{}");

        WarehouseExportTaskEntity completedTask = WarehouseExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(operatorId).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        // When
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务仍 COMPLETED（降级语义，CO-582 §4）
        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(1)).complete(any());
        verify(stateService, times(0)).fail(anyLong(), any());
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

        when(filterService.filterAll(filterDTO)).thenThrow(new RuntimeException("数据库连接失败"));

        // When
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务 FAILED
        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(0)).complete(any());
        verify(stateService, times(1)).fail(eq(taskId), any());
    }

    @Test
    void executeExport_whenErrorThrown_shouldFailTaskAndRethrow() {
        Long taskId = 4L;
        Long operatorId = 100L;
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        // 模拟 OutOfMemoryError
        when(filterService.filterAll(filterDTO)).thenThrow(new OutOfMemoryError("GC overhead limit exceeded"));

        // When & Then: Error 被重新抛出
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                asyncExecutor.executeExport(taskId, filterDTO, operatorId, "bid_admin",
                        scope, forms, System.currentTimeMillis()))
                .isInstanceOf(OutOfMemoryError.class);

        // Then: 任务被标记 FAILED（CO-469 第四轮教训：必须 catch Error）
        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(0)).complete(any());
        verify(stateService, times(1)).fail(eq(taskId), any());
    }

    @Test
    void executeExportByIds_shouldMarkProcessingThenCompleteTask() throws Exception {
        Long taskId = 5L;
        Long operatorId = 100L;
        List<Long> ids = List.of(10L, 20L);
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        WarehouseEntity entity = WarehouseEntity.builder().id(10L).createdBy(operatorId).build();
        when(filterService.findAllByIds(ids)).thenReturn(List.of(entity));
        when(attachmentRepo.findByWarehouseIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(any())).thenReturn(List.of());
        when(excelWriter.write(any(), anyList())).thenReturn(new byte[]{1, 2, 3});
        doNothing().when(wordBundleBuilder).buildBundle(anyList(), any(), any());

        Path zipFile = Files.createFile(tempDir.resolve("test_ids.zip"));
        WarehouseExportZipBuilder.ZipBuildResult zipResult = new WarehouseExportZipBuilder.ZipBuildResult(
                zipFile, 100L, new WarehouseExportZipBuilder.ZipStats());
        when(zipBuilder.buildZip(any(), anyList(), any(), any(), eq(forms))).thenReturn(zipResult);
        when(exportPublisher.buildResultSummaryJson(anyInt(), any(), any(), anyLong(), any()))
                .thenReturn("{}");

        WarehouseExportTaskEntity completedTask = WarehouseExportTaskEntity.builder()
                .id(taskId).status(ExportStatus.COMPLETED).createdBy(operatorId).build();
        when(stateService.complete(any())).thenReturn(completedTask);

        // When
        asyncExecutor.executeExportByIds(taskId, ids, operatorId, "bid_admin",
                scope, forms, System.currentTimeMillis());

        // Then: 任务状态 PROCESSING → COMPLETED
        verify(stateService, times(1)).markProcessing(taskId);
        verify(stateService, times(1)).complete(any());
        verify(stateService, times(0)).fail(anyLong(), any());
    }
}
