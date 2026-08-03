package com.xiyu.bid.performance.application;

import java.time.Duration;

/**
 * 业绩合订本导出完成上下文 — 封装 {@link PerformanceBundleExportTaskStateService#complete} 的参数，
 * 避免长参数列。仅作为状态机方法入参容器，不含业务逻辑。
 */
public record PerformanceExportCompletion(
        Long taskId,
        int totalCount,
        String filePath,
        String resultSummary,
        Duration fileTtl,
        long startMs
) {}
