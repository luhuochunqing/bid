package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private WarehouseExportAsyncExecutor asyncExecutor;

    @Mock
    private WarehouseExportTaskStateService stateService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WarehouseExportAppService appService;

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
}
