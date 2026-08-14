package com.xiyu.bid.analytics.service;

import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 部门 DISTINCT（users.department_name）。
     * 仅返回当前用户可见项目关联的部门。
     */
    List<String> fetchDistinctDepartments() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct u.departmentName
                        from Project p
                        join User u on u.id = p.managerId
                        where u.departmentName is not null
                          and u.departmentName <> ''
                          and (:allAccess = true or p.id in :projectIds)
                        order by u.departmentName
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 人员 DISTINCT（users.full_name）。
     * 可选按部门名称过滤（PRD §6.4 部门-人员联动用，null 表示不过滤返回全部）。
     * 注：fetchDistinctDepartments 返回 departmentName 作为下拉 value，
     * 此处按 departmentName 过滤保持语义一致（同名部门对应同 code）。
     */
    List<String> fetchDistinctPersons(List<String> departmentNames) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;

        StringBuilder jpql = new StringBuilder("""
                select distinct u.fullName
                from Project p
                join User u on u.id = p.managerId
                where u.fullName is not null
                  and u.fullName <> ''
                  and (:allAccess = true or p.id in :projectIds)
                """);
        if (departmentNames != null && !departmentNames.isEmpty()) {
            jpql.append("  and u.departmentName in :departmentNames\n");
        }
        jpql.append(" order by u.fullName");

        var query = entityManager.createQuery(jpql.toString(), String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds);
        if (departmentNames != null && !departmentNames.isEmpty()) {
            query.setParameter("departmentNames", departmentNames);
        }
        return query.getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 区域 DISTINCT（project_initiation_details.headquarters_location）。
     */
    List<String> fetchDistinctRegions() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct pid.headquartersLocation
                        from Project p
                        join ProjectInitiationDetails pid on pid.projectId = p.id
                        where pid.headquartersLocation is not null
                          and pid.headquartersLocation <> ''
                          and (:allAccess = true or p.id in :projectIds)
                        order by pid.headquartersLocation
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 客户类型 DISTINCT（projects.customer_type）。
     */
    List<String> fetchDistinctCustomerTypes() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct p.customerType
                        from Project p
                        where p.customerType is not null
                          and p.customerType <> ''
                          and (:allAccess = true or p.id in :projectIds)
                        order by p.customerType
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 项目类型 DISTINCT（tenders.project_type）。
     */
    List<String> fetchDistinctProjectTypes() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct t.projectType
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where t.projectType is not null
                          and t.projectType <> ''
                          and (:allAccess = true or p.id in :projectIds)
                        order by t.projectType
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 招标主体 DISTINCT（tenders.purchaser_name）。
     * 与 CompetitorAnalysisQueryService.fetchDistinctTenderEntities 不同：
     * 此处基于所有可见项目（不限制必须有竞品记录）。
     */
    List<String> fetchDistinctTenderEntitiesForFilter() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct t.purchaserName
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where t.purchaserName is not null
                          and t.purchaserName <> ''
                          and (:allAccess = true or p.id in :projectIds)
                        order by t.purchaserName
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 竞品公司 DISTINCT（project_result_competitor.name）。
     */
    List<String> fetchDistinctCompetitorNames() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct prc.name
                        from ProjectResultCompetitor prc
                        join ProjectResult pr on pr.id = prc.resultId
                        join Project p on p.id = pr.projectId
                        where prc.name is not null
                          and prc.name <> ''
                          and (:allAccess = true or p.id in :projectIds)
                        order by prc.name
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    private Set<Long> scopedProjectIds() {
        if (projectAccessScopeService.currentUserHasAdminAccess()) {
            return null;
        }
        List<Long> allowedIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        if (allowedIds == null || allowedIds.isEmpty()) {
            return Set.of();
        }
        return allowedIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
