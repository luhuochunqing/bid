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
import org.springframework.transaction.annotation.Transactional;

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
 * 职责：
 * 1. 创建 PENDING 导出任务记录（@Transactional）
 * 2. 委托调用 WarehouseExportAsyncExecutor 执行异步导出
 * 3. 提供任务查询和文件下载能力
 *
 * 注意：@Async 方法已提取到 WarehouseExportAsyncExecutor。
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
    private final ObjectMapper objectMapper;

    /**
     * 创建导出任务，触发异步执行。
     */
    @Transactional
    public ExportTaskResult export(WarehouseFilterDTO filterDTO, Long operatorId,
                                   String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                                   Set<WarehouseAttachmentOrganizationForm> attachmentForms) {
        String filterSnapshot = serializeFilter(filterDTO);
        WarehouseExportTaskEntity task = createTask(filterSnapshot, operatorId);
        asyncExecutor.executeExport(task.getId(), filterDTO, operatorId, operatorUsername,
                attachmentScope, attachmentForms, System.currentTimeMillis());
        return new ExportTaskResult(task.getId());
    }

    /**
     * 创建按 ID 批量导出的任务。
     */
    @Transactional
    public ExportTaskResult exportByIds(List<Long> ids, Long operatorId,
                                        String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                                        Set<WarehouseAttachmentOrganizationForm> attachmentForms) {
        String filterSnapshot = serializeIds(ids);
        WarehouseExportTaskEntity task = createTask(filterSnapshot, operatorId);
        asyncExecutor.executeExportByIds(task.getId(), ids, operatorId, operatorUsername,
                attachmentScope, attachmentForms, System.currentTimeMillis());
        return new ExportTaskResult(task.getId());
    }

    private WarehouseExportTaskEntity createTask(String filterSnapshot, Long operatorId) {
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(filterSnapshot)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
        return exportTaskRepo.save(task);
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
        return Files.readAllBytes(path);
    }

    public record ExportTaskResult(Long taskId) {}
}
