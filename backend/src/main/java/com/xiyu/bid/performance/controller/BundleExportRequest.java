package com.xiyu.bid.performance.controller;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;

import java.util.List;
import java.util.Set;

/**
 * 业绩合订本导出请求 DTO。
 *
 * <p>替代 Controller 中 {@code Map<String, Object>} 手动解析请求体的方式，
 * 提供类型安全的请求绑定。
 *
 * <p>两种模式：
 * <ul>
 *   <li>勾选模式：提供 {@code ids}，按指定业绩记录导出</li>
 *   <li>筛选模式：提供 {@code criteria}，按筛选条件导出</li>
 * </ul>
 *
 * @param ids             勾选的业绩记录 ID 列表（勾选模式）
 * @param criteria        筛选条件（筛选模式）
 * @param attachmentTypes 附件类型筛选；空集合表示全部
 */
public record BundleExportRequest(
        List<Long> ids,
        PerformanceSearchCriteria criteria,
        Set<String> attachmentTypes
) {
}
