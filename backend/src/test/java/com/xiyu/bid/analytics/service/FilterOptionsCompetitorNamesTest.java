// Input: EntityManager mocked JPQL results (snapshot distinct + ACTIVE dictionary), ProjectAccessScopeService
// Output: fetchDistinctCompetitorNames 并集口径断言（specs/056）
// Pos: Test/竞品筛选下拉并集单测
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("specs/056: M1/M4 竞品下拉选项 = 快照引用 ∪ ACTIVE 字典")
class FilterOptionsCompetitorNamesTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private TypedQuery<String> snapshotQuery;

    @Mock
    private TypedQuery<String> dictionaryQuery;

    @InjectMocks
    private FilterOptionsQueryService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        // 锚点区分两条 JPQL：快照查询含 "ProjectResultCompetitor"，字典查询含 "from Competitor c"
        lenient().when(entityManager.createQuery(contains("ProjectResultCompetitor"), eq(String.class)))
                .thenReturn(snapshotQuery);
        lenient().when(entityManager.createQuery(contains("from Competitor"), eq(String.class)))
                .thenReturn(dictionaryQuery);
        lenient().when(snapshotQuery.setParameter(anyString(), any())).thenReturn(snapshotQuery);
        lenient().when(dictionaryQuery.setParameter(eq("activeStatus"), eq(CompetitorStatus.ACTIVE)))
                .thenReturn(dictionaryQuery);
        lenient().when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
    }

    @Test
    @DisplayName("快照名与 ACTIVE 字典取并集去重，Java 端自然序排序")
    void unionsSnapshotAndActiveDictionarySorted() {
        when(snapshotQuery.getResultList()).thenReturn(List.of("京东", "震坤行", "自定义新竞品"));
        when(dictionaryQuery.getResultList()).thenReturn(List.of("震坤行", "得力", "未引用新竞品"));

        List<String> result = service.fetchDistinctCompetitorNames();

        // Unicode 码点序：京(4E) < 得(5F) < 未(67) < 自(81) < 震(97)；「震坤行」双源去重只留一个
        assertThat(result).containsExactly("京东", "得力", "未引用新竞品", "自定义新竞品", "震坤行");
    }

    @Test
    @DisplayName("null 与空白名两侧均被过滤，不入选项")
    void filtersNullAndBlankNames() {
        when(snapshotQuery.getResultList()).thenReturn(java.util.Arrays.asList("有效竞品", null, "  "));
        when(dictionaryQuery.getResultList()).thenReturn(java.util.Arrays.asList("", null, "  得力  "));

        List<String> result = service.fetchDistinctCompetitorNames();

        assertThat(result).containsExactly("  得力  ", "有效竞品");
    }

    @Test
    @DisplayName("非全局权限时快照查询绑定 scopeIds 参数")
    void bindsScopeParamsForNonGlobalUser() {
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(7L, 8L));
        when(snapshotQuery.getResultList()).thenReturn(List.of("京东"));
        when(dictionaryQuery.getResultList()).thenReturn(List.of());

        List<String> result = service.fetchDistinctCompetitorNames();

        assertThat(result).containsExactly("京东");
        org.mockito.Mockito.verify(snapshotQuery).setParameter(eq("allAccess"), eq(false));
        org.mockito.Mockito.verify(snapshotQuery).setParameter(eq("scopeIds"), eq(Set.of(7L, 8L)));
    }
}
