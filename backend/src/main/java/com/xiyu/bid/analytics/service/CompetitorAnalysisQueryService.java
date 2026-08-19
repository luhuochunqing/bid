// Input: EntityManager, ProjectAccessScopeService
// Output: CompetitorAnalysisRow data lists
// Pos: Query Service/数据查询层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

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
                  and (:startDate is null or p.createdAt >= :startDate)
                  and (:endDate is null or p.createdAt <= :endDate)
                """);
        boolean hasTenderEntities = tenderEntities != null && !tenderEntities.isEmpty();
        // 模式分支：
        // 1. 招标主体模式（grouped）：按 purchaserName 过滤，不限制固定竞品枚举，以便动态发现该主体下的实际所有竞争对手；
        // 2. 默认模式（default）：按用户选中的竞品公司列表过滤。
        if (hasTenderEntities) {
            jpql.append("  and t.purchaserName in :tenderEntities\n");
        } else if (competitorNames != null && !competitorNames.isEmpty()) {
            jpql.append("  and prc.name in :competitorNames\n");
        }
        jpql.append(" order by p.createdAt desc, p.id desc");

        var query = entityManager.createQuery(jpql.toString(), CompetitorAnalysisRow.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59));
        if (hasTenderEntities) {
            query.setParameter("tenderEntities", tenderEntities);
        } else if (competitorNames != null && !competitorNames.isEmpty()) {
            query.setParameter("competitorNames", competitorNames);
        }
        return query.getResultList();
    }

    /**
     * 获取项目模式竞品记录（PRD §9.8.5 — 按项目名称过滤）。
     * 按项目名精确定位单项目，不做时间范围过滤（项目可能创建于往年）。
     */
    List<CompetitorAnalysisRow> fetchProjectCompetitorRows(
            String projectName,
            List<String> competitorNames
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
                    coalesce(nullif(pr.resultType, ''), nullif(pid.bidResultStatus, ''), cast(p.status as string)),
                    p.status,
                    p.createdAt
                )
                from ProjectResultCompetitor prc
                join ProjectResult pr on pr.id = prc.resultId
                join Project p on p.id = pr.projectId
                left join ProjectInitiationDetails pid on pid.projectId = p.id
                left join Tender t on t.id = p.tenderId
                where (:allAccess = true or p.id in :scopeIds)
                  and p.name = :projectName
                order by prc.sortOrder asc, prc.id asc
                """);
        return entityManager.createQuery(jpql.toString(), CompetitorAnalysisRow.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("projectName", projectName)
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