// Input: EntityManager mocked JPQL results (snapshot ranked by participation + ACTIVE dictionary), ProjectAccessScopeService
// Output: fetchDistinctCompetitorNames 参与项目数降序 ∪ ACTIVE 字典补齐口径断言（specs/056）
// Pos: Test/竞品筛选下拉排序单测
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.competitionintel.entity.CompetitorStatus;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("specs/056: M1/M4 竞品下拉选项 = 参与项目数降序 ∪ ACTIVE 字典补齐")
class FilterOptionsCompetitorNamesTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private TypedQuery<Object[]> snapshotQuery;

    @Mock
    private TypedQuery<String> dictionaryQuery;

    @InjectMocks
    private FilterOptionsQueryService service;

    private static Object[] row(String name, long count) {
        return new Object[]{name, count};
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        // 锚点区分两条 JPQL：排名查询含 "ProjectResultCompetitor"，字典查询含 "from Competitor c"
        lenient().when(entityManager.createQuery(contains("ProjectResultCompetitor"), eq(Object[].class)))
                .thenReturn(snapshotQuery);
        lenient().when(entityManager.createQuery(contains("from Competitor"), eq(String.class)))
                .thenReturn(dictionaryQuery);
        lenient().when(snapshotQuery.setParameter(anyString(), any())).thenReturn(snapshotQuery);
        lenient().when(dictionaryQuery.setParameter(eq("activeStatus"), eq(CompetitorStatus.ACTIVE)))
                .thenReturn(dictionaryQuery);
        lenient().when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
    }

    @Test
    @DisplayName("快照名按参与项目数降序，同数按名称序，字典独占项按名称序补齐末尾")
    void ranksByParticipationThenAppendsDictionaryOnly() {
        // DB 已按 count desc, name 排序返回：京东3项 > 得力2项 > 震坤行2项 > 企事通1项
        when(snapshotQuery.getResultList()).thenReturn(List.of(
                row("京东", 3), row("得力", 2), row("震坤行", 2), row("企事通", 1)));
        // 字典含已引用名（去重）与独占名（补齐，名称自然序）
        when(dictionaryQuery.getResultList()).thenReturn(List.of("震坤行", "得力", "未引用新竞品", "阿里竞品"));

        List<String> result = service.fetchDistinctCompetitorNames();

        // 排名段按参与数降序；字典独占项（阿里竞品/未引用新竞品）按名称自然序补齐：未(672A) < 阿(963F)
        assertThat(result).containsExactly("京东", "得力", "震坤行", "企事通", "未引用新竞品", "阿里竞品");
    }

    @Test
    @DisplayName("字典独占项按名称自然序追加（与快照排名段解耦）")
    void dictionaryOnlyNamesSortedByName() {
        when(snapshotQuery.getResultList()).thenReturn(List.<Object[]>of(row("京东", 1)));
        when(dictionaryQuery.getResultList()).thenReturn(List.of("震坤行", "得力"));

        List<String> result = service.fetchDistinctCompetitorNames();

        // 得(5F97) < 震(9707)：名称自然序
        assertThat(result).containsExactly("京东", "得力", "震坤行");
    }

    @Test
    @DisplayName("null 与空白名两侧均被过滤，不入选项")
    void filtersNullAndBlankNames() {
        when(snapshotQuery.getResultList()).thenReturn(java.util.Arrays.<Object[]>asList(
                row("有效竞品", 2), row(null, 1), row("  ", 1)));
        when(dictionaryQuery.getResultList()).thenReturn(java.util.Arrays.asList("", null, "  得力  "));

        List<String> result = service.fetchDistinctCompetitorNames();

        assertThat(result).containsExactly("有效竞品", "  得力  ");
    }

    @Test
    @DisplayName("非全局权限时排名查询绑定 scopeIds 参数")
    void bindsScopeParamsForNonGlobalUser() {
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(7L, 8L));
        when(snapshotQuery.getResultList()).thenReturn(List.<Object[]>of(row("京东", 1)));
        when(dictionaryQuery.getResultList()).thenReturn(List.of());

        List<String> result = service.fetchDistinctCompetitorNames();

        assertThat(result).containsExactly("京东");
        verify(snapshotQuery).setParameter(eq("allAccess"), eq(false));
        verify(snapshotQuery).setParameter(eq("scopeIds"), eq(Set.of(7L, 8L)));
    }
}
