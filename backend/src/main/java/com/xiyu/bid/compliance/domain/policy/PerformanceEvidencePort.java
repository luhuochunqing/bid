// Input: 匹配记录数量上限
// Output: 业绩库真实匹配记录列表（KnowledgeMatch，recordId/content 来自真实业绩记录）
// Pos: compliance/domain/policy/ - 业绩库证据端口（spec 049 US3 / T036，依赖倒置避免 compliance→performance 模块环）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.List;

/**
 * 业绩库证据查询端口（依赖倒置）.
 *
 * <p>compliance 定义契约、performance 模块提供实现（{@code PerformanceEvidenceQueryAdapter}），
 * 避免 compliance→performance 直接依赖与遗留 alerts↔performance 边构成模块环
 * （ArchitectureTest slices free-of-cycles）.
 */
public interface PerformanceEvidencePort {

    /**
     * 查询最近的真实业绩记录并转为结构化匹配结果.
     *
     * @param limit 匹配记录数量上限
     * @return 业绩库匹配记录（content 来自 DB 记录字段拼接，禁止常量文案）
     */
    List<KnowledgeMatch> findPerformanceMatches(int limit);
}
