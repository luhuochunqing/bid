package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * WarehouseExportAppService 单元测试。
 *
 * 验证修复 @Async self-invocation 后的行为：
 * 1. export() 在 @Transactional 事务中创建 PENDING 任务
 * 2. export() 委托调用 WarehouseExportAsyncExecutor（而非 this.executeExportAsync）
 * 3. exportByIds() 同理委托调用 asyncExecutor
 *
 * 根因：原代码 export() 内部直接调用 this.executeExportAsync()，
 * Spring AOP 代理不拦截 self-invocation，@Async 注解静默失效，
 * 导致 Word 合订本生成在 HTTP 线程同步执行超过 30 秒超时。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseExportAppServiceTest {

    @Mock
    private WarehouseExportTaskRepository exportTaskRepo;

    @Mock
    private WarehouseExportAsyncExecutor asyncExecutor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WarehouseExportAppService appService;

    @Test
    void export_shouldCreatePendingTaskAndDelegateToAsyncExecutor() {
        // Given
        WarehouseFilterDTO filterDTO = new WarehouseFilterDTO(null, null, null, null, null, null, null, null, null, null, null, null);
        Long operatorId = 100L;
        String operatorUsername = "bid_admin";
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        WarehouseExportTaskEntity savedTask = WarehouseExportTaskEntity.builder()
                .id(1L)
                .status(ExportStatus.PENDING)
                .createdBy(operatorId)
                .build();
        when(exportTaskRepo.save(any(WarehouseExportTaskEntity.class))).thenReturn(savedTask);

        // When
        WarehouseExportAppService.ExportTaskResult result = appService.export(
                filterDTO, operatorId, operatorUsername, scope, forms);

        // Then: 返回 taskId
        assertThat(result.taskId()).isEqualTo(1L);

        // Then: 创建了 PENDING 任务
        ArgumentCaptor<WarehouseExportTaskEntity> taskCaptor = ArgumentCaptor.forClass(WarehouseExportTaskEntity.class);
        verify(exportTaskRepo).save(taskCaptor.capture());
        WarehouseExportTaskEntity createdTask = taskCaptor.getValue();
        assertThat(createdTask.getStatus()).isEqualTo(ExportStatus.PENDING);
        assertThat(createdTask.getCreatedBy()).isEqualTo(operatorId);

        // Then: 委托调用 asyncExecutor.executeExport（而非 self-invocation）
        verify(asyncExecutor, times(1)).executeExport(
                eq(1L), eq(filterDTO), eq(operatorId), eq(operatorUsername),
                eq(scope), eq(forms), anyLong());
    }

    @Test
    void exportByIds_shouldCreatePendingTaskAndDelegateToAsyncExecutor() {
        // Given
        List<Long> ids = List.of(10L, 20L, 30L);
        Long operatorId = 100L;
        String operatorUsername = "bid_admin";
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        Set<WarehouseAttachmentOrganizationForm> forms = Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED);

        WarehouseExportTaskEntity savedTask = WarehouseExportTaskEntity.builder()
                .id(2L)
                .status(ExportStatus.PENDING)
                .createdBy(operatorId)
                .build();
        when(exportTaskRepo.save(any(WarehouseExportTaskEntity.class))).thenReturn(savedTask);

        // When
        WarehouseExportAppService.ExportTaskResult result = appService.exportByIds(
                ids, operatorId, operatorUsername, scope, forms);

        // Then: 返回 taskId
        assertThat(result.taskId()).isEqualTo(2L);

        // Then: 委托调用 asyncExecutor.executeExportByIds（而非 self-invocation）
        verify(asyncExecutor, times(1)).executeExportByIds(
                eq(2L), eq(ids), eq(operatorId), eq(operatorUsername),
                eq(scope), eq(forms), anyLong());
    }
}
