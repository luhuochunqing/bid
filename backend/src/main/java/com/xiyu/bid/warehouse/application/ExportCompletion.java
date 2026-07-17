package com.xiyu.bid.warehouse.application;

import java.time.Duration;

/**
 * 仓库导出完成上下文 — 封装 {@code WarehouseExportTaskStateService.complete} 的参数，
 * 避免 9 参数长参数列。
 *
 * <p>不包含业务逻辑，仅作为状态机方法的入参容器。
 */
public record ExportCompletion(
        Long taskId,
        int totalCount,
        String filePath,
        String resultSummary,
        Duration fileTtl,
        long startMs
) {}
