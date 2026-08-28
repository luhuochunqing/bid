// Input: 真实 EntityManager（H2）+ 最小项目/立项详情/组织部门数据
// Output: 客户类型 coalesce 口径 JPQL 与下钻原生 SQL 可解析且取值正确的断言
// Pos: Test/客户类型双源口径 JPQL 回归（覆盖 mock 测试无法验证的查询字符串）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 客户类型双源口径（coalesce 优先立项详情）JPQL 回归：
 * 纯 Mockito 单测无法验证 JPQL/native SQL 字符串可被 Hibernate 真实解析，
 * 本测试在 H2 上实际执行改动的查询路径（筛选项 DISTINCT / M2 行查询 / M1 维度分组 / 下钻）。
 * 含英文枚举回归：立项详情存 CENTRAL_SOE 等枚举名时，分组/筛选/下钻统一归一化为中文标准分类。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("客户类型 coalesce 口径 — JPQL/native SQL 真实执行回归")
class AnalyticsCustomerTypeJpqlRegressionTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrganizationDepartmentRepository departmentRepository;

    private FilterOptionsQueryService filterOptionsService;
    private CustomerTypeAnalyticsQueryService customerTypeQueryService;
    private TrendAnalysisDimensionQueryService dimensionQueryService;
    private TrendDrillDownService drillDownService;

    @BeforeEach
    void setUp() {
        ProjectAccessScopeService scope = Mockito.mock(ProjectAccessScopeService.class);
        when(scope.currentUserHasGlobalAccess()).thenReturn(true);

        filterOptionsService = new FilterOptionsQueryService(departmentRepository, scope,
                new AnalyticsDepartmentTreeGateway(departmentRepository));
        ReflectionTestUtils.setField(filterOptionsService, "entityManager", entityManager);

        customerTypeQueryService = new CustomerTypeAnalyticsQueryService(scope);
        ReflectionTestUtils.setField(customerTypeQueryService, "entityManager", entityManager);

        dimensionQueryService = new TrendAnalysisDimensionQueryService(scope);
        ReflectionTestUtils.setField(dimensionQueryService, "entityManager", entityManager);

        drillDownService = new TrendDrillDownService(scope,
                new AnalyticsDepartmentTreeGateway(departmentRepository));
        ReflectionTestUtils.setField(drillDownService, "entityManager", entityManager);

        seedData();
    }

    /** Project 必填列：name/tenderId/managerId/createdAt。 */
    private Project buildProject(String name, Project.Status status, String customerType) {
        Project p = new Project();
        p.setName(name);
        p.setCustomerType(customerType);
        p.setStatus(status);
        p.setTenderId(1L);
        p.setManagerId(999L);
        p.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        return p;
    }

    private void seedData() {
        // 项目A：立项详情=央企（优先），主表快照=民企（应被覆盖）
        Project a = buildProject("项目A-口径优先级", Project.Status.BIDDING, "民企");
        a.setCreatedAt(LocalDateTime.of(2026, 1, 5, 10, 0));
        entityManager.persist(a);
        entityManager.persist(buildDetails(a.getId(), "央企"));

        // 项目B：无立项详情，回退主表快照=政府（进行中状态，验证新口径计入投标数）
        Project b = buildProject("项目B-回退主表", Project.Status.BIDDING, "政府");
        b.setCreatedAt(LocalDateTime.of(2026, 2, 10, 10, 0));
        entityManager.persist(b);

        // 项目C：仅立项详情=事业单位，主表为空
        Project c = buildProject("项目C-仅立项详情", Project.Status.WON, null);
        entityManager.persist(c);
        entityManager.persist(buildDetails(c.getId(), "事业单位"));

        // 项目D：立项详情存英文枚举名（ProjectInitiationMapper.applyInput 真实写入口径），主表为空
        Project d = buildProject("项目D-英文枚举", Project.Status.BIDDING, null);
        d.setCreatedAt(LocalDateTime.of(2026, 3, 20, 10, 0));
        entityManager.persist(d);
        entityManager.persist(buildDetails(d.getId(), "CENTRAL_SOE"));

        entityManager.flush();
    }

    private ProjectInitiationDetails buildDetails(Long projectId, String customerType) {
        return ProjectInitiationDetails.builder()
                .projectId(projectId)
                .customerType(customerType)
                .build();
    }

    @Test
    @DisplayName("筛选选项 DISTINCT：coalesce 取值正确且按值排序")
    void distinctCustomerTypesUseCoalescedValue() {
        List<String> options = filterOptionsService.fetchDistinctCustomerTypes();

        // DISTINCT 返回双源原始值（含英文枚举种子）；H2 排序 ASCII 在前：
        // CENTRAL_SOE < 事(U+4E8B) < 央(U+592E) < 政(U+653F)
        assertThat(options).containsExactly("CENTRAL_SOE", "事业单位", "央企", "政府");
    }

    @Test
    @DisplayName("M2 行查询：构造器表达式返回 coalesce 后的客户类型原始值")
    void customerTypeRowsCarryCoalescedValue() {
        var rows = customerTypeQueryService.fetchProjectRows(null, null);

        // M2 行查询返回原始值，归一化在计算层（CustomerTypeNormalizer）完成
        assertThat(rows).extracting(r -> r.customerType())
                .containsExactlyInAnyOrder("央企", "政府", "事业单位", "CENTRAL_SOE");
    }

    @Test
    @DisplayName("M1 维度分组：双源值分组 + 筛选条件与分组同口径命中")
    void dimensionRowsGroupByCoalescedValueAndFilterMatches() {
        var rows = dimensionQueryService.fetchCustomerTypeRows(new TrendQueryCriteria(
                null, null, null, null, null,
                List.of("央企"), null, null, null, null));

        // 项目A（中文 "央企"）+ 项目D（英文 CENTRAL_SOE 归一化）均命中，标签统一为中文标准分类
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.category()).isEqualTo("央企");
            assertThat(r.status()).isEqualTo(Project.Status.BIDDING);
        });
    }

    @Test
    @DisplayName("M1 维度分组：英文枚举种子归一化为中文标准分类，筛选同口径命中")
    void dimensionRowsNormalizeEnglishEnumToChineseCategory() {
        // 不启用筛选：英文枚举 CENTRAL_SOE 归一化后与中文标准值 "央企"（项目A）合并同柱
        var allRows = dimensionQueryService.fetchCustomerTypeRows(new TrendQueryCriteria(
                null, null, null, null, null,
                null, null, null, null, null));
        assertThat(allRows).filteredOn(r -> "央企".equals(r.category())).hasSize(2);
        // 归一化失败的原始值不产生英文柱子
        assertThat(allRows).extracting(DimensionRow::category)
                .doesNotContain("CENTRAL_SOE", "GOVERNMENT", "PRIVATE");

        // 启用筛选：中文标准值筛选命中英文枚举种子（旧 coalesce IN 直匹配会漏）
        var rows = dimensionQueryService.fetchCustomerTypeRows(new TrendQueryCriteria(
                null, null, null, null, null,
                List.of("央企"), null, null, null, null));
        assertThat(rows).extracting(DimensionRow::category).containsOnly("央企");
        assertThat(rows).extracting(DimensionRow::projectId)
                .anySatisfy(id -> assertThat(id).isNotNull());
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("M1 下钻：axisValue=中文标准分类时英文枚举种子同口径命中")
    void drillDownCustomerTypeAxisMatchesNormalizedEnglishEnum() {
        var response = drillDownService.drillDown(
                "customerType", "央企", "投标数",
                null, null, null, null, null, null, null,
                null, null, null, 1, 10);

        // 项目A（立项详情中文 "央企"）+ 项目D（立项详情英文 CENTRAL_SOE）
        assertThat(response.getSummary().getTotalCount()).isEqualTo(2L);
        assertThat(response.getItems()).extracting("projectName")
                .containsExactlyInAnyOrder("项目A-口径优先级", "项目D-英文枚举");
    }
}
