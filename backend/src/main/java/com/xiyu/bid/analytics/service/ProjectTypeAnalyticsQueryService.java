package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.ProjectTypeProjectRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectTypeAnalyticsQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    List<ProjectTypeProjectRow> fetchProjectRows(LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery("""
                        select new com.xiyu.bid.analytics.model.ProjectTypeProjectRow(
                            p.id,
                            p.tenderId,
                            p.name,
                            t.title,
                            t.projectType,
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
                        where 1=1
                          and (:startDate is null or p.createdAt >= :startDate)
                          and (:endDate is null or p.createdAt <= :endDate)
                        order by p.createdAt desc, p.id desc
                        """, ProjectTypeProjectRow.class)
                .setParameter("startDate", startDate == null ? null : startDate.atStartOfDay())
                .setParameter("endDate", endDate == null ? null : endDate.atTime(23, 59, 59))
                .getResultList();
    }
}