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

    List<CompetitorAnalysisRow> fetchCompetitorRows(
            List<String> competitorNames,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Set<Long> projectIds = scopedProjectIds();
        if (projectIds != null && projectIds.isEmpty()) {
            return List.of();
        }
        Set<Long> queryProjectIds = projectIds == null ? Set.of() : projectIds;
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.model.CompetitorAnalysisRow(
                            p.id,
                            prc.name,
                            prc.discount,
                            t.purchaserName,
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
                        order by p.createdAt desc, p.id desc
                        """, CompetitorAnalysisRow.class)
                .setParameter("competitorNames", competitorNames)
                .setParameter("allAccess", projectIds == null)
                .setParameter("projectIds", queryProjectIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }

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