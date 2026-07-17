package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.domain.ImportTaskStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仓库批量导入应用服务 — 只做编排，不含业务规则。
 * 业务校验在 WarehouseImportPolicy 纯核心；
 * 行持久化在 WarehouseImportRowPersister；
 * 附件归档在 WarehouseImportAttachmentProcessor；
 * 状态机在 WarehouseImportTaskStateService；
 * 修正文件生成在 WarehouseImportCorrectionFileGenerator。
 *
 * <p><b>self-invocation 修复</b>：@Async 方法已提取到
 * {@link WarehouseImportAsyncExecutor}，通过依赖注入调用使 @Async 代理生效。
 * 原因：Spring AOP 代理不拦截同类内部方法调用（self-invocation），
 * 在本类内部调用 @Async 方法会导致注解失效。
 *
 * <p><b>事务边界设计</b>：triggerImport 标注 @Transactional 创建 PENDING 任务并提交，
 * 异步线程通过独立事务（WarehouseImportTaskStateService 的 REQUIRES_NEW）更新状态，
 * 避免 @Async + @Transactional 竞态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportAppService {

    private final WarehouseImportTaskRepository importTaskRepo;
    private final WarehouseImportAsyncExecutor asyncExecutor;
    private final WarehouseImportTaskStateService taskState;

    @Transactional
    public ImportTaskResult triggerImport(byte[] fileBytes,
                                          List<WarehouseImportAttachmentProcessor.AttachmentInput> attachments,
                                          User operator) {
        WarehouseImportTaskEntity task = WarehouseImportTaskEntity.builder()
                .status(ImportTaskStatus.PENDING)
                .sourceFilename(null)
                .createdBy(operator.getId())
                .createdByUsername(operator.getFullName() + "(" + operator.getUsername() + ")")
                .createdAt(LocalDateTime.now())
                .build();
        importTaskRepo.save(task);

        // 委托独立 Bean 调用，确保 @Async 代理生效（避免 self-invocation）
        asyncExecutor.executeImport(task.getId(), fileBytes, attachments, operator);

        return new ImportTaskResult(task.getId());
    }

    public Page<WarehouseImportTaskEntity> listTasks(Long userId, Pageable pageable) {
        return taskState.listTasks(userId, pageable);
    }

    public WarehouseImportTaskEntity getTask(Long taskId, Long userId) {
        return taskState.getTask(taskId, userId);
    }

    public byte[] getCorrectionFile(Long taskId, Long userId) throws IOException {
        return taskState.getCorrectionFile(taskId, userId);
    }

    public record RowError(int rowIndex, String message) {}

    public record ImportTaskResult(Long taskId) {}
}
