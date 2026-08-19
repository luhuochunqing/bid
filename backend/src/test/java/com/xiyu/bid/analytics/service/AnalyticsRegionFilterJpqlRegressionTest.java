// Input: 真实 EntityManager（H2）+ 多种总部所在地存储格式的项目数据
// Output: 区域筛选与图表分组/下钻同口径（筛选后数字 = 柱子之和）的断言
// Pos: Test/区域省份归一化口径 JPQL 回归（覆盖历史短省名/拼接格式混存场景）
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

    @BeforeEach
    void setUp() {
        ProjectAccessScopeService scope = Mockito.mock(ProjectAccessScopeService.class);
        when(scope.currentUserHasGlobalAccess()).thenReturn(true);

        dimensionQueryService = new TrendAnalysisDimensionQueryService(scope);
        ReflectionTestUtils.setField(dimensionQueryService, "entityManager", entityManager);

        timeQueryService = new TrendAnalysisQueryService(scope);
        ReflectionTestUtils.setField(timeQueryService, "entityManager", entityManager);

        drillDownService = new TrendDrillDownService(scope);
        ReflectionTestUtils.setField(drillDownService, "entityManager", entityManager);

        seedData();
    }

    private Project buildProject(String name, Project.Status status, String headquartersLocation) {
        Project p = new Project();
        p.setName(name);
        p.setStatus(status);
        p.setTenderId(1L);
        p.setManagerId(999L);
        p.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        entityManager.persist(p);
        entityManager.persist(ProjectInitiationDetails.builder()
                .projectId(p.getId())
                .headquartersLocation(headquartersLocation)
                .build());
        return p;
    }

    private void seedData() {
        // 规范拼接格式（旧 LIKE 能命中）
        buildProject("项目A-规范拼接", Project.Status.WON, "江苏省淮安市");
        // 历史无后缀短省名（旧 LIKE '江苏省%' 漏掉，归一化口径应命中）
        buildProject("项目B-短省名", Project.Status.BIDDING, "江苏");
        // 自由文本省+市（旧 LIKE 漏掉，归一化口径应命中）
        buildProject("项目C-自由文本", Project.Status.WON, "江苏淮安");
        // 其他省份（不应命中江苏省筛选）
        buildProject("项目D-浙江", Project.Status.BIDDING, "浙江省杭州市");
        entityManager.flush();
    }

    private TrendQueryCriteria jiangsuCriteria() {
        return new TrendQueryCriteria(null, null, null, null,
                List.of("江苏省"), null, null, null, null, null);
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
}
