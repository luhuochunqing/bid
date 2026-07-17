package com.xiyu.bid.warehouse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仓库台账导出应用服务 — 只做编排，不含业务规则。
 *
 * <p>职责：
 * <ol>
 *   <li>委托 {@link WarehouseExportTaskStateService#createTask} 创建 PENDING 任务（独立事务，提交后立即可见）</li>
 *   <li>委托 {@link WarehouseExportAsyncExecutor} 执行异步导出（@Async 代理生效）</li>
 *   <li>提供任务查询和文件下载能力</li>
 * </ol>
 *
 * <p><b>事务边界设计（P1-1 修复）</b>：本类不再标注 {@code @Transactional}。
 * 原因：createTask 由 StateService 以独立事务执行并在返回前提交，
 * 避免原 {@code @Async} + {@code @Transactional} 竞态 —— 异步线程 findById 时
 * 外层事务可能尚未 commit 导致任务丢失。
 *
 * <p>注意：@Async 方法已提取到 {@link WarehouseExportAsyncExecutor}。
 * 原因：Spring AOP 代理不拦截同类内部方法调用（self-invocation），
 * 在本类内部调用 @Async 方法会导致注解失效。通过依赖注入独立 Bean 调用，
 * 使 @Async 代理生效，导出在 warehouseExportExecutor 线程池异步执行。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportAppService {

    private final WarehouseExportTaskRepository exportTaskRepo;
    private final WarehouseExportAsyncExecutor asyncExecutor;
    private final WarehouseExportTaskStateService stateService;
    private final ObjectMapper objectMapper;

    /**
     * 创建导出任务，触发异步执行。
     * <p>无 @Transactional：stateService.createTask 以独立事务提交后立即返回 taskId，
     * 异步线程可立即查询到，避免 @Async + @Transactional 竞态。
     */
    public ExportTaskResult export(WarehouseFilterDTO filterDTO, Long operatorId,
                                   String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                                   Set<WarehouseAttachmentOrganizationForm> attachmentForms) {
        String filterSnapshot = serializeFilter(filterDTO);
        Long taskId = stateService.createTask(filterSnapshot, operatorId);
        asyncExecutor.executeExport(taskId, filterDTO, operatorId, operatorUsername,
                attachmentScope, attachmentForms, System.currentTimeMillis());
        return new ExportTaskResult(taskId);
    }

    /**
     * 创建按 ID 批量导出的任务。
     */
    public ExportTaskResult exportByIds(List<Long> ids, Long operatorId,
                                        String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                                        Set<WarehouseAttachmentOrganizationForm> attachmentForms) {
        String filterSnapshot = serializeIds(ids);
        Long taskId = stateService.createTask(filterSnapshot, operatorId);
        asyncExecutor.executeExportByIds(taskId, ids, operatorId, operatorUsername,
                attachmentScope, attachmentForms, System.currentTimeMillis());
        return new ExportTaskResult(taskId);
    }

    private String serializeFilter(WarehouseFilterDTO filterDTO) {
        try {
            return objectMapper.writeValueAsString(filterDTO);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(Map.of("ids", ids));
        } catch (JsonProcessingException e) {
            return "{\"ids\":" + ids + "}";
        }
    }

    public Page<WarehouseExportTaskEntity> listTasks(Long createdBy, Pageable pageable) {
        return exportTaskRepo.findByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
    }

    public WarehouseExportTaskEntity getTaskStatus(Long taskId, Long createdBy) {
        return exportTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在或无权限"));
    }

    public byte[] getExportFile(Long taskId, Long createdBy) throws IOException {
        WarehouseExportTaskEntity task = exportTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在或无权限"));

        if (task.getStatus() != ExportStatus.COMPLETED) {
            throw new IllegalStateException("导出任务尚未完成");
        }
        if (task.getExpiresAt() != null && LocalDateTime.now().isAfter(task.getExpiresAt())) {
            throw new IllegalStateException("导出文件已过期");
        }
        if (task.getStoredFilePath() == null) {
            throw new IllegalStateException("导出文件路径为空");
        }

        Path path = Paths.get(task.getStoredFilePath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("导出文件已被清理");
        }
        // 已知技术债（spec 039 P3-3）：单次导出最多 500 条记录 + 附件 + Word 合订本，
        // ZIP 可达数十 MB，高并发下载会撑爆堆。后续应改用 StreamingResponseBody 流式输出。
        return Files.readAllBytes(path);
    }

    public record ExportTaskResult(Long taskId) {}
}
