package com.xiyu.bid.common.application;

import java.time.Duration;

/**
 * 导出任务完成上下文（CO-602 设计评估 D3-2 修复）。
 *
 * <p>统一业绩合订本（原 {@code PerformanceExportCompletion}）与仓库导出
 * （原 {@code ExportCompletion}）的完成参数容器。两者字段完全一致，合并消除重复。
 *
 * <p>仅作为状态机方法入参容器，不含业务逻辑。
 */
public record ExportTaskCompletion(
        Long taskId,
        int totalCount,
        String filePath,
        String resultSummary,
        Duration fileTtl,
        long startMs
) {}
