// Input: OrganizationDepartmentEntity mocked dataset
// Output: AnalyticsDepartmentTreeDTO tree structure assertions
// Pos: Test/部门树形结构组装单测
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDepartmentTreeDTO;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("M1 部门树形层级组装测试")
class FilterOptionsDepartmentTreeTest {

    @Mock
    private OrganizationDepartmentRepository departmentRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @InjectMocks
    private FilterOptionsQueryService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    @DisplayName("多层级部门正确组装为父子树形结构，叶子节点 children 为 null")
    void shouldAssembleDepartmentTreeCorrectly() {
        OrganizationDepartmentEntity root = new OrganizationDepartmentEntity();
        root.setDepartmentCode("D001");
        root.setDepartmentName("销售总部");
        root.setParentDepartmentCode(null);

        OrganizationDepartmentEntity child1 = new OrganizationDepartmentEntity();
        child1.setDepartmentCode("D002");
        child1.setDepartmentName("华东区");
        child1.setParentDepartmentCode("D001");

        OrganizationDepartmentEntity subChild = new OrganizationDepartmentEntity();
        subChild.setDepartmentCode("D003");
        subChild.setDepartmentName("上海办事处");
        subChild.setParentDepartmentCode("D002");

        OrganizationDepartmentEntity standaloneRoot = new OrganizationDepartmentEntity();
        standaloneRoot.setDepartmentCode("D004");
        standaloneRoot.setDepartmentName("运营中心");
        standaloneRoot.setParentDepartmentCode(null);

        jakarta.persistence.TypedQuery<String> typedQuery = org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        when(departmentRepository.findAllByOrderByDepartmentCode())
                .thenReturn(List.of(root, child1, subChild, standaloneRoot));

        List<AnalyticsDepartmentTreeDTO> tree = service.fetchDepartmentTree();

        assertThat(tree).hasSize(2);

        // 根节点 1
        AnalyticsDepartmentTreeDTO rootNode = tree.get(0);
        assertThat(rootNode.getLabel()).isEqualTo("销售总部");
        assertThat(rootNode.getValue()).isEqualTo("销售总部");
        assertThat(rootNode.getChildren()).hasSize(1);

        // 二级节点
        AnalyticsDepartmentTreeDTO childNode = rootNode.getChildren().get(0);
        assertThat(childNode.getLabel()).isEqualTo("华东区");
        assertThat(childNode.getChildren()).hasSize(1);

        // 三级叶子节点
        AnalyticsDepartmentTreeDTO leafNode = childNode.getChildren().get(0);
        assertThat(leafNode.getLabel()).isEqualTo("上海办事处");
        assertThat(leafNode.getChildren()).isNull(); // 叶子节点 children 清理为 null

        // 根节点 2
        AnalyticsDepartmentTreeDTO standaloneNode = tree.get(1);
        assertThat(standaloneNode.getLabel()).isEqualTo("运营中心");
        assertThat(standaloneNode.getChildren()).isNull();
    }

    @Test
    @DisplayName("当组织架构表为空时，自动从 User 表补齐部门作为根节点")
    void shouldFallbackToUserDepartmentsWhenOrganizationTableIsEmpty() {
        when(departmentRepository.findAllByOrderByDepartmentCode())
                .thenReturn(List.of());

        jakarta.persistence.TypedQuery<String> typedQuery = org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of("技术部", "市场部"));

        List<AnalyticsDepartmentTreeDTO> tree = service.fetchDepartmentTree();

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getLabel()).isEqualTo("技术部");
        assertThat(tree.get(0).getValue()).isEqualTo("技术部");
        assertThat(tree.get(0).getChildren()).isNull();
        assertThat(tree.get(1).getLabel()).isEqualTo("市场部");
        assertThat(tree.get(1).getValue()).isEqualTo("市场部");
        assertThat(tree.get(1).getChildren()).isNull();
    }
}
