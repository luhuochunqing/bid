// Input: OrganizationDepartmentRepository, EntityManager, ProjectAccessScopeService
// Output: Filter options, distinct values, and department tree
// Pos: Query Service/数据查询层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDepartmentTreeDTO;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PRD §6.2 M1 筛选区下拉选项查询 — 7 个维度的 DISTINCT 值。
 * 从 TrendAnalysisQueryService 拆分以控制单文件行数（≤300 行）。
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FilterOptionsQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    private final OrganizationDepartmentRepository organizationDepartmentRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 部门（扁平列表，向后兼容）。
     * 从 OSS 组织架构表（organization_departments）获取全量部门，不过滤失效部门。
     */
    List<String> fetchDistinctDepartments() {
        return organizationDepartmentRepository.findAllByOrderByDepartmentCode()
                .stream()
                .map(OrganizationDepartmentEntity::getDepartmentName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 部门树形结构。
     * 利用 parentDepartmentCode 构建层级关系，无效部门不过滤。
     * 返回格式：{ label, value(=departmentName), children }，前端 el-tree-select 直接使用。
     */
    List<AnalyticsDepartmentTreeDTO> fetchDepartmentTree() {
        List<OrganizationDepartmentEntity> allDepts =
                organizationDepartmentRepository.findAllByOrderByDepartmentCode();

        // code → DTO 索引
        Map<String, AnalyticsDepartmentTreeDTO> codeToDtoMap = new LinkedHashMap<>();
        for (OrganizationDepartmentEntity dept : allDepts) {
            String name = dept.getDepartmentName();
            if (name == null || name.isEmpty()) continue;
            codeToDtoMap.put(dept.getDepartmentCode(), AnalyticsDepartmentTreeDTO.builder()
                    .label(name)
                    .value(name)
                    .children(new ArrayList<>())
                    .build());
        }

        // 组装树：将子节点挂到父节点的 children 下
        List<AnalyticsDepartmentTreeDTO> roots = new ArrayList<>();
        for (OrganizationDepartmentEntity dept : allDepts) {
            String code = dept.getDepartmentCode();
            AnalyticsDepartmentTreeDTO dto = codeToDtoMap.get(code);
            if (dto == null) continue;

            String parentCode = dept.getParentDepartmentCode();
            if (parentCode != null && !parentCode.isEmpty() && codeToDtoMap.containsKey(parentCode)) {
                codeToDtoMap.get(parentCode).getChildren().add(dto);
            } else {
                roots.add(dto);
            }
        }

        // 清理空 children（设为 null，减少序列化体积）
        cleanEmptyChildren(roots);
        return roots;
    }

    private static void cleanEmptyChildren(List<AnalyticsDepartmentTreeDTO> nodes) {
        if (nodes == null) return;
        for (AnalyticsDepartmentTreeDTO node : nodes) {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            } else {
                cleanEmptyChildren(node.getChildren());
            }
        }
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 人员 DISTINCT（users.full_name）。
     * 直接从 User 表查询，不限制必须有项目（无效人员不过滤，全部包含）。
     * 当 departmentNames 为空时返回全量人员；有部门时按部门名称或部门编码匹配。
     */
    List<String> fetchDistinctPersons(List<String> departmentNames) {
        if (departmentNames == null || departmentNames.isEmpty()) {
            // 注意：全量加载用于初始人员下拉选项呈现，当用户量巨大时可考虑引入分页或检索提示
            return entityManager.createQuery("""
                            select distinct u.fullName
                            from User u
                            where u.fullName is not null
                              and u.fullName <> ''
                            order by u.fullName
                            """, String.class)
                    .getResultList();
        }

        // 优化子查询性能：先通过仓库预查匹配的部门编码集合，避免在 SQL 中进行双重关联子查询
        List<OrganizationDepartmentEntity> matchedDepts =
                organizationDepartmentRepository.findByDepartmentNameIn(departmentNames);
        java.util.Set<String> targetCodes = new java.util.HashSet<>();
        for (OrganizationDepartmentEntity dept : matchedDepts) {
            if (dept.getDepartmentCode() != null && !dept.getDepartmentCode().isEmpty()) {
                targetCodes.add(dept.getDepartmentCode());
            }
            if (dept.getExternalDeptId() != null && !dept.getExternalDeptId().isEmpty()) {
                targetCodes.add(dept.getExternalDeptId());
            }
        }

        return entityManager.createQuery("""
                        select distinct u.fullName
                        from User u
                        where u.fullName is not null
                          and u.fullName <> ''
                          and (
                            u.departmentName in :departmentNames
                            or (:hasCodes = true and u.departmentCode in :targetCodes)
                          )
                        order by u.fullName
                        """, String.class)
                .setParameter("departmentNames", departmentNames)
                .setParameter("hasCodes", !targetCodes.isEmpty())
                .setParameter("targetCodes", targetCodes.isEmpty() ? java.util.Set.of("") : targetCodes)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 区域 DISTINCT（project_initiation_details.headquarters_location）。
     */
    List<String> fetchDistinctRegions() {
        // 项目级数据权限：仅返回当前用户可见项目的区域选项
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select distinct pid.headquartersLocation
                        from Project p
                        join ProjectInitiationDetails pid on pid.projectId = p.id
                        where (:allAccess = true or p.id in :scopeIds)
                          and pid.headquartersLocation is not null
                          and pid.headquartersLocation <> ''
                        order by pid.headquartersLocation
                        """, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 客户类型 DISTINCT。
     * 口径与统计一致：优先立项详情值，回退项目主表快照（coalesce）。
     */
    List<String> fetchDistinctCustomerTypes() {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select distinct coalesce(nullif(pid.customerType, ''), p.customerType)
                        from Project p
                        left join ProjectInitiationDetails pid on pid.projectId = p.id
                        where (:allAccess = true or p.id in :scopeIds)
                          and coalesce(nullif(pid.customerType, ''), p.customerType) is not null
                          and coalesce(nullif(pid.customerType, ''), p.customerType) <> ''
                        order by coalesce(nullif(pid.customerType, ''), p.customerType)
                        """, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 项目类型 DISTINCT（tenders.project_type）。
     */
    List<String> fetchDistinctProjectTypes() {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select distinct t.projectType
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where (:allAccess = true or p.id in :scopeIds)
                          and t.projectType is not null
                          and t.projectType <> ''
                        order by t.projectType
                        """, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 招标主体 DISTINCT（tenders.purchaser_name）。
     * 与 CompetitorAnalysisQueryService.fetchDistinctTenderEntities 不同：
     * 此处基于所有可见项目（不限制必须有竞品记录）。
     */
    List<String> fetchDistinctTenderEntitiesForFilter() {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select distinct t.purchaserName
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where (:allAccess = true or p.id in :scopeIds)
                          and t.purchaserName is not null
                          and t.purchaserName <> ''
                        order by t.purchaserName
                        """, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 竞品公司 DISTINCT（project_result_competitor.name）。
     */
    List<String> fetchDistinctCompetitorNames() {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select distinct prc.name
                        from ProjectResultCompetitor prc
                        join ProjectResult pr on pr.id = prc.resultId
                        join Project p on p.id = pr.projectId
                        where (:allAccess = true or p.id in :scopeIds)
                          and prc.name is not null
                          and prc.name <> ''
                        order by prc.name
                        """, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .getResultList();
    }
}