package com.xiyu.bid.tender.dto;

/**
 * 标讯导入任务创建响应 DTO（POST /api/tenders/import 返回 202）。
 *
 * @param taskId        任务 ID（UUID）
 * @param status        任务状态（创建时为 PENDING）
 * @param totalRows     总行数（创建时为 0，解析后填充）
 * @param processedRows 已处理行数（创建时为 0）
 * @param successCount  成功数（创建时为 0）
 * @param failureCount  失败数（创建时为 0）
 * @param message       用户可读消息
 */
public record TenderImportTaskDTO(
        String taskId,
        String status,
        int totalRows,
        int processedRows,
        int successCount,
        int failureCount,
        String message
) {
}
