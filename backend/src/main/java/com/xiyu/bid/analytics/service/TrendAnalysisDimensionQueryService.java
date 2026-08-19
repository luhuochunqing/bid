// Input: EntityManager, ProjectAccessScopeService, TrendQueryCriteria
// Output: DimensionRow data lists
// Pos: Query Service/数据查询层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private final ProjectAccessScopeService projectAccessScopeService;

    //=== 部门 ===//
    List<DimensionRow> fetchDeptRows(TrendQueryCriteria criteria) {
        return queryDimension("u.departmentName",
                "join User u on u.id = p.managerId left join Tender t on t.id = p.tenderId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "u.departmentName is not null and u.departmentName <> ''",
                criteria, null);
    }

    //=== 人员 ===//
    List<DimensionRow> fetchPersonRows(TrendQueryCriteria criteria) {
        return queryDimension("u.fullName",
                "join User u on u.id = p.managerId left join Tender t on t.id = p.tenderId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "u.fullName is not null and u.fullName <> ''",
                criteria, null);
    }

    //=== 区域 ===//
    List<DimensionRow> fetchRegionRows(TrendQueryCriteria criteria) {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        LocalDate startDate = criteria.startDate();
        LocalDate endDate = criteria.endDate();
        List<String> userIds = criteria.userIds();
        List<String> regionIds = criteria.regionIds();
        List<String> customerTypes = criteria.customerTypes();
        List<String> projectTypes = criteria.projectTypes();
        List<Project.Status> statuses = criteria.statuses();
        List<String> tenderEntities = criteria.tenderEntities();
        List<String> competitorNames = criteria.competitorNames();
        List<String> deptIds = criteria.departmentIds();

        StringBuilder jpql = new StringBuilder("""
                select new com.xiyu.bid.analytics.service.RegionDimensionRawRow(
                    pid.headquartersLocation,
                    t.region,
                    p.id,
                    p.status
                )
                from Project p
                left join ProjectInitiationDetails pid on pid.projectId = p.id
                left join Tender t on t.id = p.tenderId
                left join User u on u.id = p.managerId
                left join ProjectResult pr on pr.projectId = p.id
                left join ProjectResultCompetitor prc on prc.resultId = pr.id
                where ((pid.headquartersLocation is not null and pid.headquartersLocation <> '') or (t.region is not null and t.region <> ''))
                  and (:allAccess = true or p.id in :scopeIds)
                """);

        if (startDate != null) jpql.append(" and p.createdAt >= :startDate");
        if (endDate != null) jpql.append(" and p.createdAt <= :endDate");
        if (isNotEmpty(userIds)) jpql.append(" and u.fullName in :userIds");
        if (isNotEmpty(regionIds)) {
            jpql.append(" and (");
            for (int i = 0; i < regionIds.size(); i++) {
                if (i > 0) jpql.append(" or ");
                jpql.append("(pid.headquartersLocation like :regionPattern").append(i)
                        .append(" or (pid.headquartersLocation is null and t.region like :regionPattern").append(i).append("))");
            }
            jpql.append(")");
        }
        if (isNotEmpty(customerTypes)) {
            jpql.append(" and (pid.customerType in :customerTypes or (pid.customerType is null and p.customerType in :customerTypes))");
        }
        if (isNotEmpty(projectTypes)) jpql.append(" and t.projectType in :projectTypes");
        if (isNotEmpty(statuses)) jpql.append(" and p.status in :statuses");
        if (isNotEmpty(tenderEntities)) jpql.append(" and t.purchaserName in :tenderEntities");
        if (isNotEmpty(competitorNames)) jpql.append(" and prc.name in :competitorNames");
        if (isNotEmpty(deptIds)) jpql.append(" and u.departmentName in :deptIds");

        var query = entityManager.createQuery(jpql.toString(), RegionDimensionRawRow.class);
        query.setParameter("allAccess", allAccess);
        query.setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds);
        if (startDate != null) query.setParameter("startDate", startDate.atStartOfDay());
        if (endDate != null) query.setParameter("endDate", endDate.atTime(23, 59, 59));
        if (isNotEmpty(userIds)) query.setParameter("userIds", userIds);
        if (isNotEmpty(regionIds)) {
            for (int i = 0; i < regionIds.size(); i++) {
                query.setParameter("regionPattern" + i, regionIds.get(i) + "%");
            }
        }
        if (isNotEmpty(customerTypes)) query.setParameter("customerTypes", customerTypes);
        if (isNotEmpty(projectTypes)) query.setParameter("projectTypes", projectTypes);
        if (isNotEmpty(statuses)) query.setParameter("statuses", statuses);
        if (isNotEmpty(tenderEntities)) query.setParameter("tenderEntities", tenderEntities);
        if (isNotEmpty(competitorNames)) query.setParameter("competitorNames", competitorNames);
        if (isNotEmpty(deptIds)) query.setParameter("deptIds", deptIds);

        List<RegionDimensionRawRow> rawList = query.getResultList();
        List<DimensionRow> rows = new ArrayList<>(rawList.size());
        for (RegionDimensionRawRow raw : rawList) {
            String rawLoc = raw.pidHeadquartersLocation() != null && !raw.pidHeadquartersLocation().isBlank()
                    ? raw.pidHeadquartersLocation()
                    : raw.tenderRegion();
            String prov = RegionDimensionExtractor.extractProvince(rawLoc);
            rows.add(new DimensionRow(prov, raw.projectId(), raw.status()));
        }
        return rows;
    }

    //=== 客户类型 ===//
    /** 客户类型取值口径：优先立项详情（详情页可编辑值），回退项目主表创建时快照。 */
    List<DimensionRow> fetchCustomerTypeRows(TrendQueryCriteria criteria) {
        return queryDimension("coalesce(nullif(pid.customerType, ''), p.customerType)",
                "left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "(pid.customerType is not null and pid.customerType <> '') or (p.customerType is not null and p.customerType <> '')",
                criteria, null);
    }

    //=== 项目类型 ===//
    List<DimensionRow> fetchProjectTypeRows(TrendQueryCriteria criteria) {
        return queryDimension("t.projectType",
                "join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "t.projectType is not null and t.projectType <> ''",
                criteria, null);
    }

    //=== 项目状态 ===//
    List<DimensionRow> fetchStatusRows(TrendQueryCriteria criteria) {
        return queryDimension("cast(p.status as string)",
                "left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "1=1",
                criteria, null);
    }

    //=== 招标主体 ===//
    List<DimensionRow> fetchTenderEntityRows(TrendQueryCriteria criteria) {
        return queryDimension("t.purchaserName",
                "join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id left join ProjectResult pr on pr.projectId = p.id left join ProjectResultCompetitor prc on prc.resultId = pr.id",
                "t.purchaserName is not null and t.purchaserName <> ''",
                criteria, null);
    }

    //=== 竞品公司 ===//
    List<DimensionRow> fetchCompetitorRows(TrendQueryCriteria criteria) {
        return queryDimension("prc.name",
                "join ProjectResult pr on pr.projectId = p.id join ProjectResultCompetitor prc on prc.resultId = pr.id left join Tender t on t.id = p.tenderId left join User u on u.id = p.managerId left join ProjectInitiationDetails pid on pid.projectId = p.id",
                "prc.name is not null and prc.name <> ''",
                criteria, null);
    }

    //=== 共享查询构建器 ===//

    @SuppressWarnings("unchecked")
    private List<DimensionRow> queryDimension(
            String selectExpr, String joinClause, String whereClause,
            TrendQueryCriteria criteria, List<String> departmentOverride) {

        List<String> deptIds = departmentOverride != null ? departmentOverride : criteria.departmentIds();
        // 项目级数据权限：非全局角色仅可见授权范围内项目（防御式兜底）
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        LocalDate startDate = criteria.startDate();
        LocalDate endDate = criteria.endDate();
        List<String> userIds = criteria.userIds();
        List<String> regionIds = criteria.regionIds();
        List<String> customerTypes = criteria.customerTypes();
        List<String> projectTypes = criteria.projectTypes();
        List<Project.Status> statuses = criteria.statuses();
        List<String> tenderEntities = criteria.tenderEntities();
        List<String> competitorNames = criteria.competitorNames();

        StringBuilder jpql = new StringBuilder("select new com.xiyu.bid.analytics.service.DimensionRow(")
                .append(selectExpr).append(", p.id, p.status) from Project p ")
                .append(joinClause).append(" where (").append(whereClause)
                .append(") and (:allAccess = true or p.id in :scopeIds)");

        if (startDate != null) jpql.append(" and p.createdAt >= :startDate");
        if (endDate != null) jpql.append(" and p.createdAt <= :endDate");
        if (isNotEmpty(userIds)) jpql.append(" and u.fullName in :userIds");
        if (isNotEmpty(regionIds)) {
            jpql.append(" and (");
            for (int i = 0; i < regionIds.size(); i++) {
                if (i > 0) jpql.append(" or ");
                jpql.append("(pid.headquartersLocation like :regionPattern").append(i)
                        .append(" or (pid.headquartersLocation is null and t.region like :regionPattern").append(i).append("))");
            }
            jpql.append(")");
        }
        if (isNotEmpty(customerTypes)) {
            jpql.append(" and (pid.customerType in :customerTypes or (pid.customerType is null and p.customerType in :customerTypes))");
        }
        if (isNotEmpty(projectTypes)) jpql.append(" and t.projectType in :projectTypes");
        if (isNotEmpty(statuses)) jpql.append(" and p.status in :statuses");
        if (isNotEmpty(tenderEntities)) jpql.append(" and t.purchaserName in :tenderEntities");
        if (isNotEmpty(competitorNames)) jpql.append(" and prc.name in :competitorNames");
        if (isNotEmpty(deptIds)) jpql.append(" and u.departmentName in :departmentIds");

        var query = entityManager.createQuery(jpql.toString(), DimensionRow.class);
        query.setParameter("allAccess", allAccess);
        query.setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds);
        if (startDate != null) query.setParameter("startDate", startDate.atStartOfDay());
        if (endDate != null) query.setParameter("endDate", endDate.atTime(23, 59, 59));
        if (isNotEmpty(userIds)) query.setParameter("userIds", userIds);
        if (isNotEmpty(regionIds)) {
            for (int i = 0; i < regionIds.size(); i++) {
                query.setParameter("regionPattern" + i, regionIds.get(i) + "%");
            }
        }
        if (isNotEmpty(customerTypes)) query.setParameter("customerTypes", customerTypes);
        if (isNotEmpty(projectTypes)) query.setParameter("projectTypes", projectTypes);
        if (isNotEmpty(statuses)) query.setParameter("statuses", statuses);
        if (isNotEmpty(tenderEntities)) query.setParameter("tenderEntities", tenderEntities);
        if (isNotEmpty(competitorNames)) query.setParameter("competitorNames", competitorNames);
        if (isNotEmpty(deptIds)) query.setParameter("departmentIds", deptIds);

        return query.getResultList();
    }

    /** 判断列表非空 */
    private static boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}