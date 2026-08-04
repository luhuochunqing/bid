package com.xiyu.bid.performance.controller.dto;

import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;

import java.util.Map;

/**
 * 业绩合订本导出任务响应 DTO（CO-602 设计评估 D1-2 修复）。
 *
 * <p>替代 Controller 中 {@code Map<String, Object>} 手动构造响应的方式，
 * 提供类型安全的响应模型，便于前端类型推导和 IDE 支持。
 *
 * @param id 任务 ID
 * @param status 任务状态（PENDING / PROCESSING / COMPLETED / FAILED）
 * @param totalCount 导出记录总数
 * @param downloadUrl 下载 URL（任务完成后才有值）
 * @param expiresAt 过期时间（格式：yyyy-MM-dd HH:mm:ss）
 * @param createdAt 创建时间
 * @param completedAt 完成时间
 * @param failureReason 失败原因（仅 FAILED 状态有值）
 * @param resultSummary 结果摘要 JSON（包含记录数、文件大小、耗时等）
 */
public record ExportTaskResponse(
        Long id,
        String status,
        int totalCount,
        String downloadUrl,
        String expiresAt,
        String createdAt,
        String completedAt,
        String failureReason,
        Map<String, Object> resultSummary
) {
    /**
     * 从实体构造响应 DTO，空值字段自动填充默认值。
     */
    public static ExportTaskResponse from(PerformanceExportTaskEntity t,
                                          java.util.function.Function<String, Map<String, Object>> summaryParser,
                                          java.time.format.DateTimeFormatter dtFmt) {
        return new ExportTaskResponse(
                t.getId(),
                t.getStatus().name(),
                t.getTotalCount() != null ? t.getTotalCount() : 0,
                t.getDownloadUrl() != null ? t.getDownloadUrl() : "",
                format(t.getExpiresAt(), dtFmt),
                format(t.getCreatedAt(), dtFmt),
                format(t.getCompletedAt(), dtFmt),
                t.getFailureReason() != null ? t.getFailureReason() : "",
                summaryParser.apply(t.getResultSummary())
        );
    }

    private static String format(java.time.LocalDateTime dt, java.time.format.DateTimeFormatter dtFmt) {
        return dt != null ? dt.format(dtFmt) : null;
    }
}
