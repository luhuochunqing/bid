package com.xiyu.bid.analytics.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 部门 DISTINCT（users.department_name）。
     */
    List<String> fetchDistinctDepartments() {
        return entityManager.createQuery("""
                        select distinct u.departmentName
                        from Project p
                        join User u on u.id = p.managerId
                        where u.departmentName is not null
                          and u.departmentName <> ''
                        order by u.departmentName
                        """, String.class)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 人员 DISTINCT（users.full_name）。
     * 可选按部门名称过滤（PRD §6.4 部门-人员联动用，null 表示不过滤返回全部）。
     * 注：fetchDistinctDepartments 返回 departmentName 作为下拉 value，
     * 此处按 departmentName 过滤保持语义一致（同名部门对应同 code）。
     */
    List<String> fetchDistinctPersons(List<String> departmentNames) {
        StringBuilder jpql = new StringBuilder("""
                select distinct u.fullName
                from Project p
                join User u on u.id = p.managerId
                where u.fullName is not null
                  and u.fullName <> ''
                """);
        if (departmentNames != null && !departmentNames.isEmpty()) {
            jpql.append("  and u.departmentName in :departmentNames\n");
        }
        jpql.append(" order by u.fullName");

        var query = entityManager.createQuery(jpql.toString(), String.class);
        if (departmentNames != null && !departmentNames.isEmpty()) {
            query.setParameter("departmentNames", departmentNames);
        }
        return query.getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 区域 DISTINCT（project_initiation_details.headquarters_location）。
     */
    List<String> fetchDistinctRegions() {
        return entityManager.createQuery("""
                        select distinct pid.headquartersLocation
                        from Project p
                        join ProjectInitiationDetails pid on pid.projectId = p.id
                        where pid.headquartersLocation is not null
                          and pid.headquartersLocation <> ''
                        order by pid.headquartersLocation
                        """, String.class)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 客户类型 DISTINCT（projects.customer_type）。
     */
    List<String> fetchDistinctCustomerTypes() {
        return entityManager.createQuery("""
                        select distinct p.customerType
                        from Project p
                        where p.customerType is not null
                          and p.customerType <> ''
                        order by p.customerType
                        """, String.class)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 项目类型 DISTINCT（tenders.project_type）。
     */
    List<String> fetchDistinctProjectTypes() {
        return entityManager.createQuery("""
                        select distinct t.projectType
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where t.projectType is not null
                          and t.projectType <> ''
                        order by t.projectType
                        """, String.class)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 招标主体 DISTINCT（tenders.purchaser_name）。
     * 与 CompetitorAnalysisQueryService.fetchDistinctTenderEntities 不同：
     * 此处基于所有可见项目（不限制必须有竞品记录）。
     */
    List<String> fetchDistinctTenderEntitiesForFilter() {
        return entityManager.createQuery("""
                        select distinct t.purchaserName
                        from Project p
                        join Tender t on t.id = p.tenderId
                        where t.purchaserName is not null
                          and t.purchaserName <> ''
                        order by t.purchaserName
                        """, String.class)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 竞品公司 DISTINCT（project_result_competitor.name）。
     */
    List<String> fetchDistinctCompetitorNames() {
        return entityManager.createQuery("""
                        select distinct prc.name
                        from ProjectResultCompetitor prc
                        join ProjectResult pr on pr.id = prc.resultId
                        join Project p on p.id = pr.projectId
                        where prc.name is not null
                          and prc.name <> ''
                        order by prc.name
                        """, String.class)
                .getResultList();
    }
}