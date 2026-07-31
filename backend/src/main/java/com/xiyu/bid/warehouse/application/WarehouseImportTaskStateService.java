package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仓库导入任务状态管理：状态机推进、计数更新、报告与修正文件路径写入、列表查询、企微通知。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseImportTaskStateService {

    private final WarehouseImportTaskRepository importTaskRepo;
    private final WeComPushService weComPushService;

    @Transactional
    public void setStatus(Long taskId, ImportTaskStatus status) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            importTaskRepo.save(task);
        });
    }

    @Transactional
    public void updateCounts(Long taskId, int total, int valid, int invalid) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setTotalRows(total);
            task.setValidRows(valid);
            task.setInvalidRows(invalid);
            importTaskRepo.save(task);
        });
    }

    @Transactional
    public void completeWithErrors(Long taskId, List<WarehouseImportAppService.RowError> errors) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ImportTaskStatus.COMPLETED);
            task.setErrorDetails(formatErrors(errors));
            task.setCompletedAt(LocalDateTime.now());
            importTaskRepo.save(task);
        });
    }

    @Transactional
    public void complete(Long taskId, int imported, List<WarehouseImportAppService.RowError> errors,
                         WarehouseImportAttachmentProcessor.AttachmentResult attachResult,
                         String correctionPath) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ImportTaskStatus.COMPLETED);
            task.setImportedRows(imported);
            task.setInvalidRows(errors.size());
            StringBuilder sb = new StringBuilder();
            if (correctionPath != null) {
                sb.append("[CORRECTION_FILE]").append(correctionPath).append("\n");
            }
            if (attachResult != null) {
                sb.append("[ATTACH_RESULT] matched=").append(attachResult.matchedCount())
                        .append(" unmatched=").append(attachResult.unmatched().size()).append("\n");
                for (WarehouseImportAttachmentProcessor.UnmatchedFile u : attachResult.unmatched()) {
                    sb.append("[UNMATCHED] ").append(u.filename()).append(" | ").append(u.reason()).append("\n");
                }
            }
            sb.append(formatErrors(errors));
            task.setErrorDetails(sb.toString());
            task.setCompletedAt(LocalDateTime.now());
            importTaskRepo.save(task);
            publishImportDoneNotification(task, imported, errors.size(),
                    attachResult != null ? attachResult.matchedCount() : 0,
                    attachResult != null ? attachResult.unmatched().size() : 0);
        });
    }

    private void publishImportDoneNotification(WarehouseImportTaskEntity task, int imported, int failed,
                                                int attached, int unmatched) {
        try {
            String title = String.format("📥 仓库信息导入 — %s",
                    failed == 0 ? "完成" : "完成（含失败）");
            String body = String.format(
                    "成功 %d 条 | 失败 %d 条 | 关联附件 %d 个 | 未匹配附件 %d 个",
                    imported, failed, attached, unmatched);
            // 与 TenderReminderJob 同构：直推企微。publishEvent(notificationId=null) 无人消费（见 Export 对应注释）
            NotificationCreatedEvent event = new NotificationCreatedEvent(
                    null,
                    List.of(task.getCreatedBy()),
                    "WAREHOUSE_IMPORT",
                    title,
                    body,
                    "WAREHOUSE_IMPORT_TASK",
                    task.getId(),
                    null
            );
            weComPushService.pushForRecipient(event, task.getCreatedBy());
            log.info("仓库导入完成通知已发布: taskId={}, imported={}, failed={}, attached={}, unmatched={}",
                    task.getId(), imported, failed, attached, unmatched);
        } catch (RuntimeException e) {
            log.warn("发布仓库导入完成通知失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    @Transactional
    public void fail(Long taskId, String reason) {
        importTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ImportTaskStatus.FAILED);
            task.setFailureReason(reason);
            task.setCompletedAt(LocalDateTime.now());
            importTaskRepo.save(task);
        });
    }

    public Page<WarehouseImportTaskEntity> listTasks(Long createdBy, Pageable pageable) {
        return importTaskRepo.findByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
    }

    public WarehouseImportTaskEntity getTask(Long taskId, Long createdBy) {
        return importTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在或无权限"));
    }

    public byte[] getCorrectionFile(Long taskId, Long createdBy) throws IOException {
        WarehouseImportTaskEntity task = getTask(taskId, createdBy);
        if (task.getErrorDetails() == null) {
            throw new IllegalStateException("该任务没有修正文件");
        }
        for (String line : task.getErrorDetails().split("\n")) {
            if (line.startsWith("[CORRECTION_FILE]")) {
                String path = line.substring("[CORRECTION_FILE]".length());
                return Files.readAllBytes(Paths.get(path));
            }
        }
        throw new IllegalStateException("未找到修正文件路径");
    }

    private static String formatErrors(List<WarehouseImportAppService.RowError> errors) {
        if (errors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (WarehouseImportAppService.RowError e : errors) {
            sb.append("第 ").append(e.rowIndex()).append(" 行: ").append(e.message()).append("\n");
        }
        return sb.toString();
    }
}
