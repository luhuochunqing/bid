package com.xiyu.bid.performance.application.dto;

import java.util.Set;

/**
 * 业绩 ZIP 导出条件值对象。
 *
 * <p>承载附件类型筛选参数：attachmentTypes 为 null 或空 = 全量导出（向后兼容）。
 */
public record PerformanceExportCriteria(
        Set<String> attachmentTypes
) {
    /** 全量导出（不按类型筛选）的工厂方法。 */
    public static PerformanceExportCriteria allTypes() {
        return new PerformanceExportCriteria(Set.of());
    }

    /** 是否需要按类型筛选。 */
    public boolean shouldFilter() {
        return attachmentTypes != null && !attachmentTypes.isEmpty();
    }
}
