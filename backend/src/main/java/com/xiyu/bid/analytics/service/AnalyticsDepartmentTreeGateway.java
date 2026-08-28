// Input: OrganizationDepartmentRepository（organization_departments 全量）
// Output: List<AnalyticsDepartmentHierarchySupport.DeptNode> 纯 record 视图（脏行跳过）
// Pos: Gateway/组织部门树读取网关（analytics 域内部门层级口径的唯一数据源）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组织部门树读取网关：为部门两级展示口径（{@link AnalyticsDepartmentHierarchySupport}）
 * 提供纯 record 数据源，供趋势图表、下钻明细、人员联动选项三方共用同一棵树。
 *
 * <p>独立于 {@code FilterOptionsQueryService}（该类职责是"筛选区下拉选项 DISTINCT 值"，
 * 组织树原始结构不是选项），避免下钻/图表为了借维度数据而依赖选项查询服务。</p>
 *
 * <p>命名以 Gateway 结尾：本项目权限门禁 {@code ProjectAccessGuardCoverageTest} 的 candidate
 * 判据是 Controller/Service/Guard 后缀，本类只读维度表、不触碰 projectId，无需登记基线。</p>
 */
@Component
@RequiredArgsConstructor
public class AnalyticsDepartmentTreeGateway {

    private final OrganizationDepartmentRepository organizationDepartmentRepository;

    /**
     * 全量组织部门节点（含 source_app 分区键，同一 code 可在多棵组织树并存）。
     *
     * <p>脏行策略：code 或 name 为空的记录<b>先跳过再构造</b>——DeptNode 构造器对空 code
     * 抛 IllegalArgumentException，若在构造后才过滤，组织表一行脏数据就会让所有带部门筛选的
     * 接口整体 500，而不是降级为忽略该行。</p>
     */
    public List<AnalyticsDepartmentHierarchySupport.DeptNode> loadAll() {
        return organizationDepartmentRepository.findAllByOrderByDepartmentCode()
                .stream()
                .filter(AnalyticsDepartmentTreeGateway::isUsable)
                .map(d -> new AnalyticsDepartmentHierarchySupport.DeptNode(
                        d.getSourceApp(), d.getDepartmentCode(), d.getParentDepartmentCode(),
                        d.getDepartmentName()))
                .toList();
    }

    private static boolean isUsable(OrganizationDepartmentEntity dept) {
        String code = dept.getDepartmentCode();
        String name = dept.getDepartmentName();
        return code != null && !code.isBlank() && name != null && !name.isBlank();
    }
}
