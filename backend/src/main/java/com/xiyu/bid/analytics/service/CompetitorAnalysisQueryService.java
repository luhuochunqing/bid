package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CompetitorAnalysisQueryService {

    @PersistenceContext
    private EntityManager entityManager;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 获取竞品记录行（PRD §9.9 关联链路）。
     * 支持 tenderEntities 列表精确过滤（IN），非空时仅返回匹配的招标主体记录。
     */
    List<CompetitorAnalysisRow> fetchCompetitorRows(
            List<String> competitorNames,
            List<String> tenderEntities,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;

        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.model.CompetitorAnalysisRow(
                    p.id,
                    p.name,
                    prc.name,
                    prc.discount,
                    t.purchaserName,
                    prc.paymentTerm,
                    pr.resultType,
                    p.status,
                    p.createdAt
                )
                from ProjectResultCompetitor prc
                join ProjectResult pr on pr.id = prc.resultId
                join Project p on p.id = pr.projectId
                left join Tender t on t.id = p.tenderId
                where prc.name in :competitorNames
                  and (:allAccess = true or p.id in :projectIds)
                  and (:startDate is null or p.createdAt >= :startDate)
                  and (:endDate is null or p.createdAt <= :endDate)
                """);
        if (tenderEntities != null && !tenderEntities.isEmpty()) {
            jpql.append("  and t.purchaserName in :tenderEntities\n");
        }
        jpql.append(" order by p.createdAt desc, p.id desc");

        var query = entityManager.createQuery(jpql.toString(), CompetitorAnalysisRow.class)
                .setParameter("competitorNames", competitorNames)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59));
        if (tenderEntities != null && !tenderEntities.isEmpty()) {
            query.setParameter("tenderEntities", tenderEntities);
        }
        return query.getResultList();
    }

    /**
     * 获取项目模式竞品记录（PRD §9.8.5 — 按项目名称过滤）。
     */
    List<CompetitorAnalysisRow> fetchProjectCompetitorRows(
            String projectName,
            List<String> competitorNames,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;

        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.model.CompetitorAnalysisRow(
                    p.id,
                    p.name,
                    prc.name,
                    prc.discount,
                    t.purchaserName,
                    prc.paymentTerm,
                    pr.resultType,
                    p.status,
                    p.createdAt
                )
                from ProjectResultCompetitor prc
                join ProjectResult pr on pr.id = prc.resultId
                join Project p on p.id = pr.projectId
                left join Tender t on t.id = p.tenderId
                where p.name = :projectName
                  and prc.name in :competitorNames
                  and (:allAccess = true or p.id in :projectIds)
                  and (:startDate is null or p.createdAt >= :startDate)
                  and (:endDate is null or p.createdAt <= :endDate)
                order by prc.sortOrder asc, prc.id asc
                """);
        return entityManager.createQuery(jpql.toString(), CompetitorAnalysisRow.class)
                .setParameter("projectName", projectName)
                .setParameter("competitorNames", competitorNames)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    /**
     * 获取招标主体下拉选项（PRD §9.10 — DISTINCT purchaser_name）。
     */
    List<String> fetchDistinctTenderEntities() {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select distinct t.purchaserName
                        from ProjectResultCompetitor prc
                        join ProjectResult pr on pr.id = prc.resultId
                        join Project p on p.id = pr.projectId
                        left join Tender t on t.id = p.tenderId
                        where t.purchaserName is not null
                          and (:allAccess = true or p.id in :projectIds)
                        order by t.purchaserName
                        """, String.class)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .getResultList();
    }

    /**
     * 模糊搜索项目名称（PRD §9.3 — GET /api/analytics/project-names）。
     */
    List<String> fetchProjectNames(String query) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        String pattern = query == null || query.isBlank() ? "%" : "%" + query.trim().toLowerCase() + "%";
        return entityManager.createQuery("""
                        select distinct p.name
                        from Project p
                        join ProjectResult pr on pr.projectId = p.id
                        join ProjectResultCompetitor prc on prc.resultId = pr.id
                        where p.name is not null
                          and lower(p.name) like :pattern
                          and (:allAccess = true or p.id in :projectIds)
                        order by p.name
                        """, String.class)
                .setParameter("pattern", pattern)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .setMaxResults(50)
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
