package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.CustomerTypeProjectRow;
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
public class CustomerTypeAnalyticsQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ProjectAccessScopeService projectAccessScopeService;

    List<CustomerTypeProjectRow> fetchProjectRows(LocalDate startDate, LocalDate endDate) {
        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底，页面权限已限 GLOBAL_ACCESS_ROLES）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.model.CustomerTypeProjectRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            t.title,
                            p.customer,
                            p.customerType,
                            p.status,
                            p.managerId,
                            u.fullName,
                            coalesce(p.budget, t.budget),
                            coalesce(p.startDate, p.createdAt),
                            p.endDate,
                            t.status
                        )
                        from Project p
                        left join Tender t on t.id = p.tenderId
                        left join User u on u.id = p.managerId
                        where (:allAccess = true or p.id in :scopeIds)
                          and (:startDate is null or p.createdAt >= :startDate)
                          and (:endDate is null or p.createdAt <= :endDate)
                        order by p.createdAt desc, p.id desc
                        """, CustomerTypeProjectRow.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }
}