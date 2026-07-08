package com.xiyu.bid.tender.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯导入进度查询响应 DTO（GET /api/tenders/import/{taskId}/progress 返回 200）。
 *
 * @param taskId        任务 ID（UUID）
 * @param status        任务状态（PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED）
 * @param totalRows     总行数
 * @param processedRows 已处理行数
 * @param successCount  成功数
 * @param failureCount  失败数
 * @param percent       进度百分比（0-100）
 * @param errors        失败明细（仅 COMPLETED/PARTIAL_SUCCESS/FAILED 时返回，处理中为 null）
 * @param createdAt     创建时间
 * @param completedAt   完成时间（未完成为 null）
 */
public record TenderImportProgressDTO(
        String taskId,
        String status,
        int totalRows,
        int processedRows,
        int successCount,
        int failureCount,
        int percent,
        List<TenderImportTaskError> errors,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
