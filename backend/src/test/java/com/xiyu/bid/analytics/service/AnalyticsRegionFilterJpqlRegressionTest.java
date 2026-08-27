// Input: 真实 EntityManager（H2）+ 多种总部所在地存储格式的项目数据
// Output: 区域筛选与图表分组/下钻同口径（筛选后数字 = 柱子之和）的断言
// Pos: Test/区域省份归一化口径 JPQL 回归（覆盖历史短省名/拼接格式混存场景）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 区域筛选省份归一化口径回归：
 * 历史存量数据存在多种总部所在地格式（规范拼接 "江苏省淮安市"、无后缀短省名 "江苏"），
 * 旧实现对原始串做 LIKE '省名%' 前缀匹配，与图表分组的 Java 端归一化口径不一致，
 * 导致筛选计数与柱子数对不上。本测试在 H2 上验证筛选/分组/下钻三处统一口径。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("区域筛选省份归一化口径 — 筛选/分组/下钻一致性回归")
class AnalyticsRegionFilterJpqlRegressionTest {

    @Autowired
    private EntityManager entityManager;

    private TrendAnalysisDimensionQueryService dimensionQueryService;
    private TrendAnalysisQueryService timeQueryService;
    private TrendDrillDownService drillDownService;

    /** 项目 F 的 ID：pid.customerType 空串 + p.customerType='政府'（空串语义裂缝回归种子） */
    private Long projectFId;

    /** 项目 A 的 ID：受限权限回归用例中唯一授权可见的项目 */
    private Long projectAId;

    /** 日期边界种子：G 在截止日 23:59:59 之后（下钻口径内），H 在截止日次日（范围外） */
    private Long boundaryInId;
    private Long boundaryOutId;

    @BeforeEach
    void setUp() {
        ProjectAccessScopeService scope = Mockito.mock(ProjectAccessScopeService.class);
        when(scope.currentUserHasGlobalAccess()).thenReturn(true);

        dimensionQueryService = new TrendAnalysisDimensionQueryService(scope);
        ReflectionTestUtils.setField(dimensionQueryService, "entityManager", entityManager);

        timeQueryService = new TrendAnalysisQueryService(scope);
        ReflectionTestUtils.setField(timeQueryService, "entityManager", entityManager);

        drillDownService = new TrendDrillDownService(scope, Mockito.mock(FilterOptionsQueryService.class));
        ReflectionTestUtils.setField(drillDownService, "entityManager", entityManager);

        seedData();
    }

    private Project buildProject(String name, Project.Status status, String headquartersLocation) {
        return buildProjectAt(name, status, headquartersLocation, LocalDateTime.of(2026, 3, 15, 10, 0));
    }

    /** @PrePersist 覆盖 createdAt，边界用例需 persist 后回写精确时间戳 */
    private void forceCreatedAt(Long projectId, LocalDateTime createdAt) {
        entityManager.createQuery("update Project p set p.createdAt = :ts where p.id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", projectId)
                .executeUpdate();
    }

    private Project buildProjectAt(String name, Project.Status status, String headquartersLocation,
                                   LocalDateTime createdAt) {
        Project p = new Project();
        p.setName(name);
        p.setStatus(status);
        p.setTenderId(1L);
        p.setManagerId(999L);
        p.setCreatedAt(createdAt);
        entityManager.persist(p);
        entityManager.persist(ProjectInitiationDetails.builder()
                .projectId(p.getId())
                .headquartersLocation(headquartersLocation)
                .build());
        return p;
    }

    private void seedData() {
        // 规范拼接格式（旧 LIKE 能命中）
        projectAId = buildProject("项目A-规范拼接", Project.Status.WON, "江苏省淮安市").getId();
        // 历史无后缀短省名（旧 LIKE '江苏省%' 漏掉，归一化口径应命中）
        buildProject("项目B-短省名", Project.Status.BIDDING, "江苏");
        // 自由文本省+市（旧 LIKE 漏掉，归一化口径应命中）
        buildProject("项目C-自由文本", Project.Status.WON, "江苏淮安");
        // 其他省份（不应命中江苏省筛选）
        buildProject("项目D-浙江", Project.Status.BIDDING, "浙江省杭州市");
        // 总部所在地为空串，回退标讯区域 t.region（验证双源回退分支端到端）
        Tender tender = Tender.builder().title("测试招标公告").region("浙江省杭州市").build();
        entityManager.persist(tender);
        Project e = new Project();
        e.setName("项目E-回退标讯区域");
        e.setStatus(Project.Status.WON);
        e.setTenderId(tender.getId());
        e.setManagerId(999L);
        e.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        entityManager.persist(e);
        entityManager.persist(ProjectInitiationDetails.builder()
                .projectId(e.getId())
                .headquartersLocation("")
                .build());
        // 客户类型空串语义裂缝种子：pid.customerType='' 但 p.customerType='政府'，
        // 分组柱子取值走 coalesce 回退到 '政府'，筛选/下钻必须同口径命中
        Project f = new Project();
        f.setName("项目F-客户类型空串回退");
        f.setStatus(Project.Status.WON);
        f.setCustomerType("政府");
        f.setTenderId(1L);
        f.setManagerId(999L);
        f.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        entityManager.persist(f);
        entityManager.persist(ProjectInitiationDetails.builder()
                .projectId(f.getId())
                .headquartersLocation("安徽省合肥市")
                .customerType("")
                .build());
        projectFId = f.getId();
        // 日期边界种子（新疆，不干扰既有江苏/浙江断言）：
        // G = 截止日 23:59:59.500（旧 Support 用 23:59:59 上界会漏），H = 截止日次日（超范围）；
        // Project @PrePersist 会覆盖 createdAt，须 persist 后用 UPDATE 回写精确时间戳
        Project g = buildProjectAt("项目G-边界内", Project.Status.BIDDING, "新疆维吾尔自治区乌鲁木齐市",
                LocalDateTime.of(2026, 3, 16, 23, 59, 59, 500_000_000));
        Project h = buildProjectAt("项目H-边界外", Project.Status.BIDDING, "新疆维吾尔自治区克拉玛依市",
                LocalDateTime.of(2026, 3, 17, 0, 0, 1));
        forceCreatedAt(g.getId(), LocalDateTime.of(2026, 3, 16, 23, 59, 59, 500_000_000));
        forceCreatedAt(h.getId(), LocalDateTime.of(2026, 3, 17, 0, 0, 1));
        boundaryInId = g.getId();
        boundaryOutId = h.getId();
        entityManager.flush();
    }

    private TrendQueryCriteria jiangsuCriteria() {
        return new TrendQueryCriteria(null, null, null, null,
                List.of("江苏省"), null, null, null, null, null);
    }

    private TrendQueryCriteria governmentCriteria() {
        // 筛选参数用标准分类口径：主表快照变体 "政府" 归一化后命中（CustomerTypeNormalizer）
        return new TrendQueryCriteria(null, null, null, null,
                null, List.of("政府机关/事业单位/高校"), null, null, null, null);
    }

    @Test
    @DisplayName("客户类型空串语义：pid.customerType='' 回退主表快照，筛标准分类在分组/时间/下钻均命中")
    void customerTypeEmptyStringFallsBackToProjectSnapshot() {
        // 维度分组：筛标准分类必须命中项目 F（旧 is null 口径会漏掉空串），
        // 分组标签同步归一化：变体 "政府" 归入标准柱子 "政府机关/事业单位/高校"
        var rows = dimensionQueryService.fetchCustomerTypeRows(governmentCriteria());
        assertThat(rows).allSatisfy(r -> assertThat(r.category()).isEqualTo("政府机关/事业单位/高校"));
        assertThat(rows).extracting(DimensionRow::projectId).contains(projectFId);

        // 时间维度（month/week）同口径命中
        var monthRows = timeQueryService.fetchTimeTrendRows(governmentCriteria(), "month");
        assertThat(monthRows).extracting(TrendAnalysisQueryService.TimeDimensionRow::projectId)
                .contains(projectFId);
        var weekRows = timeQueryService.fetchTimeTrendRows(governmentCriteria(), "week");
        assertThat(weekRows).extracting(TrendAnalysisQueryService.TimeDimensionRow::projectId)
                .contains(projectFId);

        // 下钻通用筛选命中
        var drill = drillDownService.drillDown(
                "time", null, "投标数",
                null, null, null, null, null, List.of("政府机关/事业单位/高校"), null, null,
                null, null, 1, 10);
        assertThat(drill.getSummary().getTotalCount()).isEqualTo(1L);
        assertThat(drill.getItems()).extracting("projectId").containsExactly(projectFId);

        // 下钻 customerType 轴点击同口径命中（axisValue 为归一化后的柱子标签）
        var axisDrill = drillDownService.drillDown(
                "customerType", "政府机关/事业单位/高校", "投标数",
                null, null, null, null, null, null, null, null,
                null, null, 1, 10);
        assertThat(axisDrill.getSummary().getTotalCount()).isEqualTo(1L);
        assertThat(axisDrill.getItems()).extracting("projectId").containsExactly(projectFId);
    }

    @Test
    @DisplayName("区域分组：筛选后行数 = 所有命中格式之和（含短省名与自由文本）")
    void regionRowsFilterMatchesNormalizedProvince() {
        var rows = dimensionQueryService.fetchRegionRows(jiangsuCriteria());

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(r -> assertThat(r.category()).isEqualTo("江苏省"));
        assertThat(rows).extracting(DimensionRow::projectId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("区域+日期联合过滤：endDate 边界（23:59:59 后区间）与下钻一致不静默丢项目")
    void regionFilterWithDateRangeBoundaryAlignedWithDrillDown() {
        LocalDate start = LocalDate.of(2026, 3, 16);
        LocalDate end = LocalDate.of(2026, 3, 16);

        // 下钻主查询口径 <= endDate 次日零点：G（23:59:59.500）命中、H（次日）不命中；
        // 区域命中集必须同口径（旧 Support 用 23:59:59 上界会静默漏掉 G）
        var drill = drillDownService.drillDown(
                "time", null, "投标数",
                start, end, null, null, List.of("新疆维吾尔自治区"), null, null, null,
                null, null, 1, 10);
        assertThat(drill.getSummary().getTotalCount()).isEqualTo(1L);
        assertThat(drill.getItems()).extracting("projectId").containsExactly(boundaryInId);

        // region 轴点击 + 日期联合同样命中 G、不含 H
        var regionAxisDrill = drillDownService.drillDown(
                "region", "新疆维吾尔自治区", "投标数",
                start, end, null, null, null, null, null, null,
                null, null, 1, 10);
        assertThat(regionAxisDrill.getSummary().getTotalCount()).isEqualTo(1L);
        assertThat(regionAxisDrill.getItems()).extracting("projectId")
                .containsExactly(boundaryInId)
                .doesNotContain(boundaryOutId);

        // 对照：同日期窗口换未命中省份返回空
        var miss = drillDownService.drillDown(
                "time", null, "投标数",
                start, end, null, null, List.of("浙江省"), null, null, null,
                null, null, 1, 10);
        assertThat(miss.getSummary().getTotalCount()).isZero();
    }

    @Test
    @DisplayName("非全局权限角色：区域命中集与下游均受授权范围限制（scopeIds 分支生效）")
    void regionFilterRespectsRestrictedProjectScope() {
        ProjectAccessScopeService restricted = Mockito.mock(ProjectAccessScopeService.class);
        when(restricted.currentUserHasGlobalAccess()).thenReturn(false);
        when(restricted.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(projectAId));

        var restrictedDimension = new TrendAnalysisDimensionQueryService(restricted);
        ReflectionTestUtils.setField(restrictedDimension, "entityManager", entityManager);
        var restrictedDrillDown = new TrendDrillDownService(restricted, Mockito.mock(FilterOptionsQueryService.class));
        ReflectionTestUtils.setField(restrictedDrillDown, "entityManager", entityManager);

        // 维度分组共享入口：江苏省命中 A/B/C，但授权范围仅 A 可见
        var rows = restrictedDimension.fetchStatusRows(new TrendQueryCriteria(
                null, null, null, null, List.of("江苏省"), null, null, null, null, null));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).projectId()).isEqualTo(projectAId);

        // 下钻同样受限：仅 A 可见
        var drill = restrictedDrillDown.drillDown(
                "time", null, "投标数",
                null, null, null, null, List.of("江苏省"), null, null, null,
                null, null, 1, 10);
        assertThat(drill.getSummary().getTotalCount()).isEqualTo(1L);
        assertThat(drill.getItems()).extracting("projectId").containsExactly(projectAId);
    }

    @Test
    @DisplayName("区域分组：未启用区域筛选时按归一化省份分组（短省名并入江苏省柱子）")
    void regionRowsGroupByNormalizedProvinceWithoutFilter() {
        var rows = dimensionQueryService.fetchRegionRows(new TrendQueryCriteria(
                null, null, null, null, null, null, null, null, null, null));

        long jiangsuCount = rows.stream()
                .filter(r -> "江苏省".equals(r.category()))
                .count();
        assertThat(jiangsuCount).isEqualTo(3);
    }

    @Test
    @DisplayName("时间维度：区域筛选同样生效（历史缺口补齐）")
    void timeTrendRowsRespectRegionFilter() {
        var rows = timeQueryService.fetchTimeTrendRows(jiangsuCriteria(), "month");

        assertThat(rows).hasSize(3);
    }

    @Test
    @DisplayName("时间维度 week 粒度：区域筛选同样生效")
    void weeklyTimeTrendRowsRespectRegionFilter() {
        var rows = timeQueryService.fetchTimeTrendRows(jiangsuCriteria(), "week");

        assertThat(rows).hasSize(3);
    }

    @Test
    @DisplayName("双源回退：总部所在地为空串时命中 t.region 省份")
    void regionFilterFallsBackToTenderRegion() {
        var rows = dimensionQueryService.fetchRegionRows(new TrendQueryCriteria(
                null, null, null, null,
                List.of("浙江省"), null, null, null, null, null));

        // 项目D（pid 规范拼接）+ 项目E（pid 空串回退 t.region）
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.category()).isEqualTo("浙江省"));
        assertThat(rows).extracting(DimensionRow::projectId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("空集语义：筛选无命中省份返回空而非全量")
    void regionFilterWithoutMatchReturnsEmpty() {
        var rows = dimensionQueryService.fetchRegionRows(new TrendQueryCriteria(
                null, null, null, null,
                List.of("青海省"), null, null, null, null, null));
        assertThat(rows).isEmpty();

        var drillDownResponse = drillDownService.drillDown(
                "time", null, "投标数",
                null, null, null, null, List.of("青海省"), null, null,
                null, null, null, 1, 10);
        assertThat(drillDownResponse.getSummary().getTotalCount()).isZero();
        assertThat(drillDownResponse.getItems()).isEmpty();

        var weeklyRows = timeQueryService.fetchTimeTrendRows(new TrendQueryCriteria(
                null, null, null, null,
                List.of("青海省"), null, null, null, null, null), "week");
        assertThat(weeklyRows).isEmpty();
    }

    @Test
    @DisplayName("下钻 regions 过滤：条数 = 柱子数字（含短省名存量数据）")
    void drillDownRegionsFilterMatchesNormalizedProvince() {
        var response = drillDownService.drillDown(
                "time", null, "投标数",
                null, null, null, null, List.of("江苏省"), null, null,
                null, null, null, 1, 10);

        assertThat(response.getSummary().getTotalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("下钻 axisValue=region：条数 = 对应柱子数字")
    void drillDownRegionAxisValueMatchesBarCount() {
        var response = drillDownService.drillDown(
                "region", "江苏省", "投标数",
                null, null, null, null, null, null, null,
                null, null, null, 1, 10);

        assertThat(response.getSummary().getTotalCount()).isEqualTo(3L);
        assertThat(response.getItems()).hasSize(3);
    }

    @Test
    @DisplayName("下钻 axisValue 与 regions 同时存在：取交集且只归一化计算一次")
    void drillDownRegionAxisValueIntersectsWithRegionsFilter() {
        // 交集命中：axisValue=江苏省 ∈ regions
        var hit = drillDownService.drillDown(
                "region", "江苏省", "投标数",
                null, null, null, null, List.of("江苏省", "浙江省"), null, null,
                null, null, null, 1, 10);
        assertThat(hit.getSummary().getTotalCount()).isEqualTo(3L);

        // 交集为空：axisValue=江苏省 ∉ regions=[浙江省]，返回空而非全量
        var miss = drillDownService.drillDown(
                "region", "江苏省", "投标数",
                null, null, null, null, List.of("浙江省"), null, null,
                null, null, null, 1, 10);
        assertThat(miss.getSummary().getTotalCount()).isZero();
    }
}
