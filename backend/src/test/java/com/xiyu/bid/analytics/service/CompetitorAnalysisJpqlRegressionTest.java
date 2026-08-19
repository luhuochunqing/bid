// Input: 真实 EntityManager（H2）+ 项目/结果确认/竞品行最小数据
// Output: 竞品分析两条 JPQL（含 prc.notes 列）可解析且 notes 正确带出的断言
// Pos: Test/M4 竞品 notes 是否中标 JPQL 回归（覆盖 mock 测试无法验证的查询字符串）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.model.CompetitorAnalysisRow;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.entity.ProjectResultCompetitor;
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
 * 竞品「是否中标」数据链路 JPQL 回归：
 * 项目详情「竞争对手情况」表格的是否中标枚举存储于 project_result_competitor.notes
 * （前端语义漂移复用），此前竞品分析查询未带出该列且以项目级状态推导导致整列误判。
 * 本测试在 H2 上实际执行改动后的两条查询，验证构造器 10 参匹配与 notes 取值。
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("M4 竞品 notes 是否中标 — JPQL 真实执行回归")
class CompetitorAnalysisJpqlRegressionTest {

    @Autowired
    private EntityManager entityManager;

    private CompetitorAnalysisQueryService queryService;

    @BeforeEach
    void setUp() {
        ProjectAccessScopeService scope = Mockito.mock(ProjectAccessScopeService.class);
        when(scope.currentUserHasGlobalAccess()).thenReturn(true);

        queryService = new CompetitorAnalysisQueryService(scope);
        ReflectionTestUtils.setField(queryService, "entityManager", entityManager);

        seedData();
    }

    private void seedData() {
        // 复现缺陷场景：项目级 WON（我司中标），竞品行各自的中标状态存于 notes
        Project p = new Project();
        p.setName("测试竞品公司");
        p.setStatus(Project.Status.WON);
        p.setTenderId(1L);
        p.setManagerId(999L);
        p.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        entityManager.persist(p);

        ProjectResult result = ProjectResult.builder()
                .projectId(p.getId())
                .resultType("WON")
                .createdAt(LocalDateTime.of(2026, 3, 20, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 20, 10, 0))
                .build();
        entityManager.persist(result);

        persistCompetitor(result.getId(), "震坤行", "92", "未中标", 0);
        persistCompetitor(result.getId(), "京东", "88", "已中标", 1);
        // 历史「其他说明」自由文本：不属于枚举
        persistCompetitor(result.getId(), "得力", "90", "价格偏高", 2);
    }

    private void persistCompetitor(Long resultId, String name, String discount, String notes, int sortOrder) {
        ProjectResultCompetitor prc = ProjectResultCompetitor.builder()
                .resultId(resultId)
                .name(name)
                .discount(discount)
                .paymentTerm("月结30天")
                .notes(notes)
                .sortOrder(sortOrder)
                .createdAt(LocalDateTime.of(2026, 3, 20, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 3, 20, 10, 0))
                .build();
        entityManager.persist(prc);
    }

    @Test
    @DisplayName("项目模式 JPQL：notes 按竞品行独立带出，项目级 WON 不覆盖竞品状态")
    void projectModeRowsCarryPerCompetitorNotes() {
        List<CompetitorAnalysisRow> rows =
                queryService.fetchProjectCompetitorRows("测试竞品公司", null);

        assertThat(rows).hasSize(3);
        // sortOrder 顺序：未中标 / 已中标 / 历史自由文本
        assertThat(rows).extracting(CompetitorAnalysisRow::notes)
                .containsExactly("未中标", "已中标", "价格偏高");
        // 项目级状态仍是 WON（数据独立存在，但不再是竞品 isWon 的来源）
        assertThat(rows).extracting(CompetitorAnalysisRow::projectStatus)
                .containsOnly(Project.Status.WON);
    }

    @Test
    @DisplayName("趋势模式 JPQL：同一 record 构造器 10 参匹配，notes 同样带出")
    void trendModeRowsCarryNotesWithSameConstructor() {
        List<CompetitorAnalysisRow> rows =
                queryService.fetchCompetitorRows(null, null, null, null);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(CompetitorAnalysisRow::notes)
                .containsExactlyInAnyOrder("未中标", "已中标", "价格偏高");
    }
}
