// Input: mocked ProjectAccessScopeService / FilterOptionsQueryService 与下钻请求参数
// Output: 部门 expand 零命中时返回空响应的断言
// Pos: Test/M1 下钻部门零命中 fail-closed
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.TrendDrillDownResponse;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("M1 下钻部门零命中 fail-closed")
class TrendDrillDownDepartmentFailClosedTest {

    /** 未锚定回退两级口径的组织树：物理三级（上海办事处）在显示截断外。 */
    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> NODES = List.of(
            new AnalyticsDepartmentHierarchySupport.DeptNode("D001", null, "销售总部"),
            new AnalyticsDepartmentHierarchySupport.DeptNode("D002", "D001", "华东区"),
            new AnalyticsDepartmentHierarchySupport.DeptNode("D003", "D002", "上海办事处"));

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @Mock
    private FilterOptionsQueryService filterOptionsQueryService;

    @Mock
    private EntityManager entityManager;

    private TrendDrillDownService service;

    @BeforeEach
    void setUp() {
        service = new TrendDrillDownService(projectAccessScopeService, filterOptionsQueryService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    @DisplayName("部门勾选落在显示截断外时零命中：直接返回空响应，不拼空 IN、不发 SQL")
    void returnsEmptyResponseWhenDepartmentExpandEmpty() {
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(filterOptionsQueryService.loadDepartmentNodes()).thenReturn(NODES);

        TrendDrillDownResponse response = service.drillDown(
                "dept", null, "投标数", null, null,
                List.of("上海办事处"), null, null, null, null, null, null, null, 1, 10);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getSummary().getTotalCount()).isZero();
        assertThat(response.getPagination().getTotal()).isZero();
        verifyNoInteractions(entityManager);
    }
}
