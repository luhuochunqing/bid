// Input: OrganizationDepartmentRepository, EntityManager, ProjectAccessScopeService
// Output: Filter options, distinct values, and department tree（竞品=快照∪ACTIVE字典并集，specs/056）
// Pos: Query Service/数据查询层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDepartmentTreeDTO;
import com.xiyu.bid.competitionintel.entity.CompetitorStatus;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

    private final OrganizationDepartmentRepository organizationDepartmentRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 部门（扁平列表，向后兼容）。
     * 从 OSS 组织架构表（organization_departments）获取全量部门，不过滤失效部门。
     */
    List<String> fetchDistinctDepartments() {
        return organizationDepartmentRepository.findAllByOrderByDepartmentCode()
                .stream()
                .map(OrganizationDepartmentEntity::getDepartmentName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 部门树形结构。
     * 利用 parentDepartmentCode 构建层级关系，无效部门不过滤。
     * 返回格式：{ label, value(=departmentName), children }，前端 el-tree-select 直接使用。
     */
    List<AnalyticsDepartmentTreeDTO> fetchDepartmentTree() {
        List<OrganizationDepartmentEntity> allDepts =
                organizationDepartmentRepository.findAllByOrderByDepartmentCode();

        // code → DTO 索引
        Map<String, AnalyticsDepartmentTreeDTO> codeToDtoMap = new LinkedHashMap<>();
        for (OrganizationDepartmentEntity dept : allDepts) {
            String name = dept.getDepartmentName();
            if (name == null || name.isEmpty()) continue;
            codeToDtoMap.put(dept.getDepartmentCode(), AnalyticsDepartmentTreeDTO.builder()
                    .label(name)
                    .value(name)
                    .children(new ArrayList<>())
                    .build());
        }

        // 组装树：将子节点挂到父节点的 children 下
        List<AnalyticsDepartmentTreeDTO> roots = new ArrayList<>();
        Set<String> presentNames = new HashSet<>();
        for (OrganizationDepartmentEntity dept : allDepts) {
            String code = dept.getDepartmentCode();
            AnalyticsDepartmentTreeDTO dto = codeToDtoMap.get(code);
            if (dto == null) continue;
            presentNames.add(dto.getValue());

            String parentCode = dept.getParentDepartmentCode();
            if (parentCode != null && !parentCode.isEmpty() && codeToDtoMap.containsKey(parentCode)) {
                codeToDtoMap.get(parentCode).getChildren().add(dto);
            } else {
                roots.add(dto);
            }
        }

        // 兜底与补齐：查询 User 表中实际存在的部门，若尚未包含在组织树中（例如环境未同步组织表），自动补齐为根节点
        List<String> userDepts = entityManager.createQuery("""
                        select distinct u.departmentName
                        from User u
                        where u.departmentName is not null
                          and u.departmentName <> ''
                        order by u.departmentName
                        """, String.class)
                .getResultList();
        for (String uDept : userDepts) {
            if (!presentNames.contains(uDept)) {
                roots.add(AnalyticsDepartmentTreeDTO.builder()
                        .label(uDept)
                        .value(uDept)
                        .build());
                presentNames.add(uDept);
            }
        }

        // 清理空 children（设为 null，减少序列化体积）
        cleanEmptyChildren(roots);
        return roots;
    }

    private static void cleanEmptyChildren(List<AnalyticsDepartmentTreeDTO> nodes) {
        if (nodes == null) return;
        for (AnalyticsDepartmentTreeDTO node : nodes) {
            if (node.getChildren() != null && node.getChildren().isEmpty()) {
                node.setChildren(null);
            } else {
                cleanEmptyChildren(node.getChildren());
            }
        }
    }

    /**
     * 部门两级展示口径（"选了一级只展示一级，只要有二级就展示二级"）的数据源：
     * 全量组织部门映射为纯 record，供 {@link AnalyticsDepartmentHierarchySupport} 做展开/分桶。
     */
    List<AnalyticsDepartmentHierarchySupport.DeptNode> fetchDepartmentNodes() {
        return organizationDepartmentRepository.findAllByOrderByDepartmentCode()
                .stream()
                .map(d -> new AnalyticsDepartmentHierarchySupport.DeptNode(
                        d.getDepartmentCode(), d.getParentDepartmentCode(), d.getDepartmentName()))
                .filter(n -> n.name() != null && !n.name().isBlank())
                .toList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 人员 DISTINCT（users.full_name）。
     * 直接从 User 表查询，不限制必须有项目（无效人员不过滤，全部包含）。
     * 当 departmentNames 为空时返回全量人员；有部门时按部门名称或部门编码匹配。
     */
    List<String> fetchDistinctPersons(List<String> departmentNames) {
        if (departmentNames == null || departmentNames.isEmpty()) {
            // 注意：全量加载用于初始人员下拉选项呈现，当用户量巨大时可考虑引入分页或检索提示
            return entityManager.createQuery("""
                            select distinct u.fullName
                            from User u
                            where u.fullName is not null
                              and u.fullName <> ''
                            order by u.fullName
                            """, String.class)
                    .getResultList();
        }

        // 部门两级口径：勾选父部门时联动选项须覆盖其全部后代部门人员，
        // 与图表统计范围（prepare.matchNames）保持一致
        var deptScope = AnalyticsDepartmentHierarchySupport.prepare(fetchDepartmentNodes(), departmentNames);
        List<String> effectiveNames = deptScope.matchNamesList() != null ? deptScope.matchNamesList() : departmentNames;
        // 优化子查询性能：先通过仓库预查匹配的部门编码集合，避免在 SQL 中进行双重关联子查询
        List<OrganizationDepartmentEntity> matchedDepts =
                organizationDepartmentRepository.findByDepartmentNameIn(effectiveNames);
        java.util.Set<String> targetCodes = new java.util.HashSet<>();
        for (OrganizationDepartmentEntity dept : matchedDepts) {
            if (dept.getDepartmentCode() != null && !dept.getDepartmentCode().isEmpty()) {
                targetCodes.add(dept.getDepartmentCode());
            }
            if (dept.getExternalDeptId() != null && !dept.getExternalDeptId().isEmpty()) {
                targetCodes.add(dept.getExternalDeptId());
            }
        }

        return entityManager.createQuery("""
                        select distinct u.fullName
                        from User u
                        where u.fullName is not null
                          and u.fullName <> ''
                          and (
                            u.departmentName in :departmentNames
                            or (:hasCodes = true and u.departmentCode in :targetCodes)
                          )
                        order by u.fullName
                        """, String.class)
                .setParameter("departmentNames", effectiveNames)
                .setParameter("hasCodes", !targetCodes.isEmpty())
                .setParameter("targetCodes", targetCodes.isEmpty() ? java.util.Set.of("") : targetCodes)
                .getResultList();
    }

    private List<String> queryScopedDistinct(String jpql) {
        Set<Long> scopeIds = AnalyticsProjectScopeSupport.scopedProjectIds(projectAccessScopeService);
        boolean allAccess = scopeIds == null;
        return entityManager.createQuery(jpql, String.class)
                .setParameter("allAccess", allAccess)
                .setParameter("scopeIds", allAccess ? Set.of(-1L) : scopeIds)
                .getResultList();
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 区域 DISTINCT（提取省级单位）。
     * 分别提取立项详情总部与标讯区域，在 Java 内存中解析并合并，彻底规避 MySQL 跨表 Collation 冲突。
     */
    List<String> fetchDistinctRegions() {
        List<String> pidLocs = queryScopedDistinct("""
                select distinct pid.headquartersLocation
                from ProjectInitiationDetails pid join Project p on p.id = pid.projectId
                where (:allAccess = true or p.id in :scopeIds)
                  and pid.headquartersLocation is not null and pid.headquartersLocation <> ''
                """);
        List<String> tenderLocs = queryScopedDistinct("""
                select distinct t.region
                from Tender t join Project p on p.tenderId = t.id
                where (:allAccess = true or p.id in :scopeIds)
                  and t.region is not null and t.region <> ''
                """);
        Set<String> merged = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String loc : pidLocs) {
            String prov = RegionDimensionExtractor.extractProvince(loc);
            if (prov != null && !prov.isBlank() && !"其他".equals(prov)) merged.add(prov);
        }
        for (String loc : tenderLocs) {
            String prov = RegionDimensionExtractor.extractProvince(loc);
            if (prov != null && !prov.isBlank() && !"其他".equals(prov)) merged.add(prov);
        }
        return new ArrayList<>(merged);
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 客户类型 DISTINCT。
     */
    List<String> fetchDistinctCustomerTypes() {
        List<String> rawTypes = queryScopedDistinct("""
                select distinct coalesce(nullif(pid.customerType, ''), p.customerType)
                from Project p left join ProjectInitiationDetails pid on pid.projectId = p.id
                where (:allAccess = true or p.id in :scopeIds)
                  and ((pid.customerType is not null and pid.customerType <> '')
                    or (pid.customerType is null and p.customerType is not null and p.customerType <> ''))
                """);
        Set<String> sorted = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String t : rawTypes) {
            if (t != null && !t.isBlank()) sorted.add(t.trim());
        }
        return new ArrayList<>(sorted);
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 项目类型 DISTINCT（tenders.project_type）。
     */
    List<String> fetchDistinctProjectTypes() {
        return queryScopedDistinct("""
                select distinct t.projectType from Project p join Tender t on t.id = p.tenderId
                where (:allAccess = true or p.id in :scopeIds) and t.projectType is not null and t.projectType <> ''
                order by t.projectType
                """);
    }

    /**
     * PRD §6.2 M1 筛选区下拉选项 — 招标主体 DISTINCT（tenders.purchaser_name）。
     */
    List<String> fetchDistinctTenderEntitiesForFilter() {
        return queryScopedDistinct("""
                select distinct t.purchaserName from Project p join Tender t on t.id = p.tenderId
                where (:allAccess = true or p.id in :scopeIds) and t.purchaserName is not null and t.purchaserName <> ''
                order by t.purchaserName
                """);
    }

    /**
     * PRD §6.2 M1/M4 筛选区下拉选项 — 竞品公司（specs/056 动态字典口径）。
     * <p>选项并集 = 结果确认页可选项（competitors 字典 ACTIVE，主数据不做项目 scope）
     * ∪ 被引用过的快照名（project_result_competitor.name distinct，带项目权限 scope，
     * 天然覆盖已停用/删除但有数据的竞品——快照只存 name 与字典解耦，见 PR !2414）。
     * 合并后 Java 端按名称自然序排序，与 DB collation 解耦。</p>
     */
    List<String> fetchDistinctCompetitorNames() {
        Set<String> names = new TreeSet<>();
        queryScopedDistinct("""
                select distinct prc.name from ProjectResultCompetitor prc
                join ProjectResult pr on pr.id = prc.resultId join Project p on p.id = pr.projectId
                where (:allAccess = true or p.id in :scopeIds) and prc.name is not null and prc.name <> ''
                """).stream()
                .filter(name -> name != null && !name.isBlank())
                .forEach(names::add);
        entityManager.createQuery(
                "select c.name from Competitor c"
                        + " where c.status = :activeStatus and c.name is not null and c.name <> ''",
                String.class)
                .setParameter("activeStatus", CompetitorStatus.ACTIVE)
                .getResultList().stream()
                .filter(name -> name != null && !name.isBlank())
                .forEach(names::add);
        return List.copyOf(names);
    }
}