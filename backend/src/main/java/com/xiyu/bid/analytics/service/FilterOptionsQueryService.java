package com.xiyu.bid.analytics.service;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
     * PRD §6.2 M1 筛选区下拉选项 — 部门。
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
     * PRD §6.2 M1 筛选区下拉选项 — 人员 DISTINCT（users.full_name）。
     * 直接从 User 表查询，不限制必须有项目。
     * 可选按部门名称过滤（PRD §6.4 部门-人员联动用，departmentNames 为空时返回空列表）。
     */
    List<String> fetchDistinctPersons(List<String> departmentNames) {
        if (departmentNames == null || departmentNames.isEmpty()) {
            // 未选部门时不返回人员，前端需提示"请先选择部门"
            return List.of();
        }
        return entityManager.createQuery("""
                        select distinct u.fullName
                        from User u
                        where u.fullName is not null
                          and u.fullName <> ''
                          and u.departmentName in :departmentNames
                        order by u.fullName
                        """, String.class)
                .setParameter("departmentNames", departmentNames)
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
     * PRD §6.2 M1 筛选区下拉选项 — 客户类型 DISTINCT（projects.customer_type）。
     */
    List<String> fetchDistinctCustomerTypes() {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select distinct p.customerType
                        from Project p
                        where (:allAccess = true or p.id in :scopeIds)
                          and p.customerType is not null
                          and p.customerType <> ''
                        order by p.customerType
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