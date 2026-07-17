package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WarehouseImportAppService 单元测试。
 *
 * <p>验证修复 @Async self-invocation 后的委托模式：
 * <ol>
 *   <li>triggerImport() 创建 PENDING 任务后委托 asyncExecutor.executeImport（而非 this.executeImportAsync）</li>
 * </ol>
 *
 * <p>根因：原代码 triggerImport() 内部直接调用 this.executeImportAsync()，
 * Spring AOP 代理不拦截 self-invocation，@Async 注解静默失效。
 * 修复：提取 @Async 方法到 WarehouseImportAsyncExecutor 独立 Bean，通过依赖注入调用使代理生效。
 */
@ExtendWith(MockitoExtension.class)
class WarehouseImportAppServiceTest {

    @Mock
    private WarehouseImportTaskRepository importTaskRepo;

    @Mock
    private WarehouseImportAsyncExecutor asyncExecutor;

    @Mock
    private WarehouseImportTaskStateService taskState;

    @InjectMocks
    private WarehouseImportAppService appService;

    @Test
    void triggerImport_shouldCreatePendingTaskAndDelegateToAsyncExecutor() {
        // Given
        User operator = User.builder().id(100L).username("operator").fullName("操作员").build();
        byte[] fileBytes = new byte[0];
        List<WarehouseImportAttachmentProcessor.AttachmentInput> attachments = List.of();

        when(importTaskRepo.save(any())).thenAnswer(inv -> {
            WarehouseImportTaskEntity task = inv.getArgument(0);
            task.setId(1L);
            return task;
        });

        // When
        WarehouseImportAppService.ImportTaskResult result = appService.triggerImport(fileBytes, attachments, operator);

        // Then: 返回 taskId
        assertThat(result.taskId()).isEqualTo(1L);

        // Then: 创建 PENDING 任务
        verify(importTaskRepo, times(1)).save(any());

        // Then: 委托调用 asyncExecutor.executeImport（而非 self-invocation）
        verify(asyncExecutor, times(1)).executeImport(eq(1L), eq(fileBytes), eq(attachments), eq(operator));
    }
}
