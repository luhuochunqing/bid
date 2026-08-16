package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
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
                where (:allAccess = true or p.id in :scopeIds)
                  and prc.name in :competitorNames
                  and (:startDate is null or p.createdAt >= :startDate)
                  and (:endDate is null or p.createdAt <= :endDate)
                """);
        if (tenderEntities != null && !tenderEntities.isEmpty()) {
            jpql.append("  and t.purchaserName in :tenderEntities\n");
        }
        jpql.append(" order by p.createdAt desc, p.id desc");

        var query = entityManager.createQuery(jpql.toString(), CompetitorAnalysisRow.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("competitorNames", competitorNames)
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
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
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
                where (:allAccess = true or p.id in :scopeIds)
                  and p.name = :projectName
                  and prc.name in :competitorNames
                  and (:startDate is null or p.createdAt >= :startDate)
                  and (:endDate is null or p.createdAt <= :endDate)
                order by prc.sortOrder asc, prc.id asc
                """);
        return entityManager.createQuery(jpql.toString(), CompetitorAnalysisRow.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("projectName", projectName)
                .setParameter("competitorNames", competitorNames)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

    /**
     * 获取招标主体下拉选项（PRD §9.10 — 基于可见项目，不限竞品记录）。
     */
    List<String> fetchDistinctTenderEntities() {
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
     * 模糊搜索项目名称（PRD §9.3 — 仅基于当前用户可见项目，防止越权枚举项目名）。
     */
    List<String> fetchProjectNames(String query) {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        String pattern = query == null || query.isBlank() ? "%" : "%" + query.trim().toLowerCase() + "%";
        return entityManager.createQuery("""
                        select distinct p.name
                        from Project p
                        where (:allAccess = true or p.id in :scopeIds)
                          and p.name is not null
                          and lower(p.name) like :pattern
                        order by p.name
                        """, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("pattern", pattern)
                .setMaxResults(50)
                .getResultList();
    }
}