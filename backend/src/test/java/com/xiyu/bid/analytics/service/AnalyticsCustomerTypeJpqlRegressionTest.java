// Input: 真实 EntityManager（H2）+ 最小项目/立项详情数据
// Output: 客户类型 coalesce 口径 JPQL 与下钻原生 SQL 可解析且取值正确的断言
// Pos: Test/客户类型双源口径 JPQL 回归（覆盖 mock 测试无法验证的查询字符串）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
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
 * 本测试在 H2 上实际执行改动的三条查询路径（筛选项 DISTINCT / M2 行查询 / M1 维度分组）。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("客户类型 coalesce 口径 — JPQL/native SQL 真实执行回归")
class AnalyticsCustomerTypeJpqlRegressionTest {

    @Autowired
    private EntityManager entityManager;

    private FilterOptionsQueryService filterOptionsService;
    private CustomerTypeAnalyticsQueryService customerTypeQueryService;
    private TrendAnalysisDimensionQueryService dimensionQueryService;
    private TrendDrillDownService drillDownService;

    @BeforeEach
    void setUp() {
        ProjectAccessScopeService scope = Mockito.mock(ProjectAccessScopeService.class);
        when(scope.currentUserHasGlobalAccess()).thenReturn(true);

        filterOptionsService = new FilterOptionsQueryService(null, scope);
        ReflectionTestUtils.setField(filterOptionsService, "entityManager", entityManager);

        customerTypeQueryService = new CustomerTypeAnalyticsQueryService(scope);
        ReflectionTestUtils.setField(customerTypeQueryService, "entityManager", entityManager);

        dimensionQueryService = new TrendAnalysisDimensionQueryService(scope);
        ReflectionTestUtils.setField(dimensionQueryService, "entityManager", entityManager);

        drillDownService = new TrendDrillDownService(scope);
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

        // H2 按字符排序：事(U+4E8B) < 央(U+592E) < 政(U+653F)
        assertThat(options).containsExactly("事业单位", "央企", "政府");
    }

    @Test
    @DisplayName("M2 行查询：构造器表达式返回 coalesce 后的客户类型")
    void customerTypeRowsCarryCoalescedValue() {
        var rows = customerTypeQueryService.fetchProjectRows(null, null);

        assertThat(rows).extracting(r -> r.customerType())
                .containsExactlyInAnyOrder("央企", "政府", "事业单位");
    }

    @Test
    @DisplayName("M1 维度分组：双源值分组 + 筛选条件走 coalesce 表达式")
    void dimensionRowsGroupByCoalescedValueAndFilterMatches() {
        var rows = dimensionQueryService.fetchCustomerTypeRows(new TrendQueryCriteria(
                null, null, null, null, null,
                List.of("央企"), null, null, null, null));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).category()).isEqualTo("央企");
        assertThat(rows.get(0).status()).isEqualTo(Project.Status.BIDDING);
    }

    @Test
    @DisplayName("M1 下钻：原生 SQL COALESCE 维度过滤与全项目投标数口径可执行")
    void drillDownFiltersCustomerTypeByCoalescedColumn() {
        var response = drillDownService.drillDown(
                "customerType", "央企", "投标数",
                null, null, null, null, null, null, null,
                null, null, null, 1, 10);

        assertThat(response.getSummary().getTotalCount()).isEqualTo(1L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProjectName()).isEqualTo("项目A-口径优先级");
    }
}
