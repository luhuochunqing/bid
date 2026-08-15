package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * M1 多维度趋势查询服务 — 按 xAxis 维度分组查询项目数据（非时间维度）。
 * 每个查询方法调用 {@link #queryDimension} 构建不同维度的 JPQL，
 * 由 {@link TrendAnalysisComputationService} 统一聚合。
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TrendAnalysisDimensionQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    //=== 部门 ===//
    List<DimensionRow> fetchDeptRows(LocalDate startDate, LocalDate endDate,
            List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes, List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {
        return queryDimension("u.departmentName",
                "join User u on u.id = p.managerId left join Tender t on t.id = p.tenderId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "u.departmentName is not null and u.departmentName <> ''",
                null, startDate, endDate, userIds, regionIds,
                customerTypes, projectTypes, statuses, tenderEntities, competitorNames);
    }

    //=== 人员 ===//
    List<DimensionRow> fetchPersonRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes, List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {
        return queryDimension("u.fullName",
                "join User u on u.id = p.managerId left join Tender t on t.id = p.tenderId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "u.fullName is not null and u.fullName <> ''",
                departmentIds, startDate, endDate, null, regionIds,
                customerTypes, projectTypes, statuses, tenderEntities, competitorNames);
    }

    //=== 区域 ===//
    List<DimensionRow> fetchRegionRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds,
            List<String> customerTypes, List<String> projectTypes, List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {
        return queryDimension("pid.headquartersLocation",
                "join ProjectInitiationDetails pid on pid.projectId = p.id left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "pid.headquartersLocation is not null and pid.headquartersLocation <> ''",
                departmentIds, startDate, endDate, userIds, null,
                customerTypes, projectTypes, statuses, tenderEntities, competitorNames);
    }

    //=== 客户类型 ===//
    List<DimensionRow> fetchCustomerTypeRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds, List<String> regionIds,
            List<String> projectTypes, List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {
        return queryDimension("p.customerType",
                "left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "p.customerType is not null and p.customerType <> ''",
                departmentIds, startDate, endDate, userIds, regionIds,
                null, projectTypes, statuses, tenderEntities, competitorNames);
    }

    //=== 项目类型 ===//
    List<DimensionRow> fetchProjectTypeRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {
        return queryDimension("t.projectType",
                "join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "t.projectType is not null and t.projectType <> ''",
                departmentIds, startDate, endDate, userIds, regionIds,
                customerTypes, null, statuses, tenderEntities, competitorNames);
    }

    //=== 项目状态 ===//
    List<DimensionRow> fetchStatusRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes,
            List<String> tenderEntities, List<String> competitorNames) {
        return queryDimension("p.status",
                "left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "1=1",
                departmentIds, startDate, endDate, userIds, regionIds,
                customerTypes, projectTypes, null, tenderEntities, competitorNames);
    }

    //=== 招标主体 ===//
    List<DimensionRow> fetchTenderEntityRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes, List<Project.Status> statuses,
            List<String> competitorNames) {
        return queryDimension("t.purchaserName",
                "join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "t.purchaserName is not null and t.purchaserName <> ''",
                departmentIds, startDate, endDate, userIds, regionIds,
                customerTypes, projectTypes, statuses, null, competitorNames);
    }

    //=== 竞品公司 ===//
    List<DimensionRow> fetchCompetitorRows(LocalDate startDate, LocalDate endDate,
            List<String> departmentIds, List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes, List<Project.Status> statuses,
            List<String> tenderEntities) {
        return queryDimension("prc.name",
                "join ProjectResult pr on pr.projectId = p.id join ProjectResultCompetitor prc on prc.resultId = pr.id left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id",
                "prc.name is not null and prc.name <> ''",
                departmentIds, startDate, endDate, userIds, regionIds,
                customerTypes, projectTypes, statuses, tenderEntities, null);
    }

    //=== 共享查询构建器 ===//

    @SuppressWarnings("unchecked")
    private List<DimensionRow> queryDimension(
            String selectExpr, String joinClause, String whereClause,
            List<String> departmentIds,
            LocalDate startDate, LocalDate endDate,
            List<String> userIds, List<String> regionIds,
            List<String> customerTypes, List<String> projectTypes, List<Project.Status> statuses,
            List<String> tenderEntities, List<String> competitorNames) {

        StringBuilder jpql = new StringBuilder("select new com.xiyu.bid.analytics.service.DimensionRow(")
                .append(selectExpr).append(", p.id, p.status) from Project p ")
                .append(joinClause).append(" where ").append(whereClause);

        if (startDate != null) jpql.append(" and p.createdAt >= :startDate");
        if (endDate != null) jpql.append(" and p.createdAt <= :endDate");
        if (userIds != null && !userIds.isEmpty()) jpql.append(" and u.fullName in :userIds");
        if (regionIds != null && !regionIds.isEmpty()) jpql.append(" and pid.headquartersLocation in :regionIds");
        if (customerTypes != null && !customerTypes.isEmpty()) jpql.append(" and p.customerType in :customerTypes");
        if (projectTypes != null && !projectTypes.isEmpty()) jpql.append(" and t.projectType in :projectTypes");
        if (statuses != null && !statuses.isEmpty()) jpql.append(" and p.status in :statuses");
        if (tenderEntities != null && !tenderEntities.isEmpty()) jpql.append(" and t.purchaserName in :tenderEntities");
        if (competitorNames != null && !competitorNames.isEmpty()) jpql.append(" and prc.name in :competitorNames");
        if (departmentIds != null && !departmentIds.isEmpty()) jpql.append(" and u.departmentName in :departmentIds");

        var query = entityManager.createQuery(jpql.toString(), DimensionRow.class);
        if (startDate != null) query.setParameter("startDate", startDate.atStartOfDay());
        if (endDate != null) query.setParameter("endDate", endDate.atTime(23, 59, 59));
        if (userIds != null && !userIds.isEmpty()) query.setParameter("userIds", userIds);
        if (regionIds != null && !regionIds.isEmpty()) query.setParameter("regionIds", regionIds);
        if (customerTypes != null && !customerTypes.isEmpty()) query.setParameter("customerTypes", customerTypes);
        if (projectTypes != null && !projectTypes.isEmpty()) query.setParameter("projectTypes", projectTypes);
        if (statuses != null && !statuses.isEmpty()) query.setParameter("statuses", statuses);
        if (tenderEntities != null && !tenderEntities.isEmpty()) query.setParameter("tenderEntities", tenderEntities);
        if (competitorNames != null && !competitorNames.isEmpty()) query.setParameter("competitorNames", competitorNames);
        if (departmentIds != null && !departmentIds.isEmpty()) query.setParameter("departmentIds", departmentIds);

        return query.getResultList();
    }
}