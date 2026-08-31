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
    @DisplayName("部门筛选树只到二级，三级不出现")
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

        // 二级节点；三级（上海办事处）不进入筛选树
        AnalyticsDepartmentTreeDTO childNode = rootNode.getChildren().get(0);
        assertThat(childNode.getLabel()).isEqualTo("华东区");
        assertThat(childNode.getChildren()).isNull();

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

    @Test
    @DisplayName("loadDepartmentNodes 跳过空白 code/name，合法行变成 DeptNode")
    void loadDepartmentNodesSkipsDirtyRows() {
        OrganizationDepartmentEntity ok = new OrganizationDepartmentEntity();
        ok.setDepartmentCode("D001");
        ok.setDepartmentName("销售总部");

        OrganizationDepartmentEntity blankName = new OrganizationDepartmentEntity();
        blankName.setDepartmentCode("D002");
        blankName.setDepartmentName(" ");
        blankName.setParentDepartmentCode("D001");

        OrganizationDepartmentEntity blankCode = new OrganizationDepartmentEntity();
        blankCode.setDepartmentCode("");
        blankCode.setDepartmentName("幽灵部门");

        when(departmentRepository.findAllByOrderByDepartmentCode())
                .thenReturn(List.of(ok, blankName, blankCode));

        assertThat(service.loadDepartmentNodes()).containsExactly(
                new AnalyticsDepartmentHierarchySupport.DeptNode("D001", null, "销售总部"));
    }

    @Test
    @DisplayName("含组织架构/rootorg虚拟根节点时，自动解包虚拟根并展示业务一二级部门")
    void shouldUnwrapVirtualRootDepartmentNode() {
        OrganizationDepartmentEntity virtualRoot = new OrganizationDepartmentEntity();
        virtualRoot.setDepartmentCode("rootorg");
        virtualRoot.setDepartmentName("组织架构");
        virtualRoot.setParentDepartmentCode(null);

        OrganizationDepartmentEntity l1Sales = new OrganizationDepartmentEntity();
        l1Sales.setDepartmentCode("D001");
        l1Sales.setDepartmentName("销售总部");
        l1Sales.setParentDepartmentCode("rootorg");

        OrganizationDepartmentEntity l2East = new OrganizationDepartmentEntity();
        l2East.setDepartmentCode("D002");
        l2East.setDepartmentName("华东区");
        l2East.setParentDepartmentCode("D001");

        OrganizationDepartmentEntity l3Shanghai = new OrganizationDepartmentEntity();
        l3Shanghai.setDepartmentCode("D003");
        l3Shanghai.setDepartmentName("上海办事处");
        l3Shanghai.setParentDepartmentCode("D002");

        OrganizationDepartmentEntity l1Ops = new OrganizationDepartmentEntity();
        l1Ops.setDepartmentCode("D004");
        l1Ops.setDepartmentName("运营中心");
        l1Ops.setParentDepartmentCode("rootorg");

        jakarta.persistence.TypedQuery<String> typedQuery = org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        when(departmentRepository.findAllByOrderByDepartmentCode())
                .thenReturn(List.of(virtualRoot, l1Sales, l2East, l3Shanghai, l1Ops));

        List<AnalyticsDepartmentTreeDTO> tree = service.fetchDepartmentTree();

        // 顶层应为真实一级部门（销售总部、运营中心），虚拟根组织架构不进入树
        assertThat(tree).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .containsExactly("销售总部", "运营中心");

        // 销售总部的一级子节点为华东区（真实二级部门）
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getLabel()).isEqualTo("华东区");
        // 华东区的三级子节点上海办事处被截断
        assertThat(tree.get(0).getChildren().get(0).getChildren()).isNull();

        // 验证 loadDepartmentNodes 也正确解包虚拟根
        List<AnalyticsDepartmentHierarchySupport.DeptNode> nodes = service.loadDepartmentNodes();
        assertThat(nodes).containsExactly(
                new AnalyticsDepartmentHierarchySupport.DeptNode("D001", null, "销售总部"),
                new AnalyticsDepartmentHierarchySupport.DeptNode("D002", "D001", "华东区"),
                new AnalyticsDepartmentHierarchySupport.DeptNode("D003", "D002", "上海办事处"),
                new AnalyticsDepartmentHierarchySupport.DeptNode("D004", null, "运营中心")
        );
    }

    @Test
    @DisplayName("锚定西域集团(0001)子树：一级=其直接子节点(锚定节点)，展示到第三级，其他根分支与User兜底不出现")
    void shouldAnchorTreeToXiYuGroupSubtree() {
        OrganizationDepartmentEntity virtualRoot = new OrganizationDepartmentEntity();
        virtualRoot.setDepartmentCode("rootorg");
        virtualRoot.setDepartmentName("组织架构");
        virtualRoot.setParentDepartmentCode(null);

        OrganizationDepartmentEntity group = new OrganizationDepartmentEntity();
        group.setDepartmentCode("0001");
        group.setDepartmentName("西域集团");
        group.setParentDepartmentCode("rootorg");

        OrganizationDepartmentEntity otherBranch = new OrganizationDepartmentEntity();
        otherBranch.setDepartmentCode("T001");
        otherBranch.setDepartmentName("测试1");
        otherBranch.setParentDepartmentCode("rootorg");

        // 锚定节点（展示一级）：西域集团的直接子节点
        OrganizationDepartmentEntity supply = new OrganizationDepartmentEntity();
        supply.setDepartmentCode("0002");
        supply.setDepartmentName("西域智慧供应链（上海）股份公司");
        supply.setParentDepartmentCode("0001");

        OrganizationDepartmentEntity westin = new OrganizationDepartmentEntity();
        westin.setDepartmentCode("0003");
        westin.setDepartmentName("威斯汀爱睿（上海）技术有限公司");
        westin.setParentDepartmentCode("0001");

        // 展示二级：锚定节点的子节点
        OrganizationDepartmentEntity bizCenter = new OrganizationDepartmentEntity();
        bizCenter.setDepartmentCode("0004");
        bizCenter.setDepartmentName("业务中心");
        bizCenter.setParentDepartmentCode("0002");

        // 展示三级：锚定节点的孙节点
        OrganizationDepartmentEntity sales1 = new OrganizationDepartmentEntity();
        sales1.setDepartmentCode("0005");
        sales1.setDepartmentName("销售一部");
        sales1.setParentDepartmentCode("0004");

        // 第四级：必须被截断
        OrganizationDepartmentEntity team1 = new OrganizationDepartmentEntity();
        team1.setDepartmentCode("0006");
        team1.setDepartmentName("一组");
        team1.setParentDepartmentCode("0005");

        // 锚点命中时不触 User 兜底查询：不 stub entityManager，若实现误调兜底将 NPE 失败
        when(departmentRepository.findAllByOrderByDepartmentCode())
                .thenReturn(List.of(virtualRoot, group, otherBranch, supply, westin, bizCenter, sales1, team1));

        List<AnalyticsDepartmentTreeDTO> tree = service.fetchDepartmentTree();

        // 一级 = 锚定节点（西域集团的直接子节点），按编码序
        assertThat(tree).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .containsExactly("西域智慧供应链（上海）股份公司", "威斯汀爱睿（上海）技术有限公司");

        // 西域集团本身、其他根分支一律不出现（锚点命中时不触 User 兜底查询）
        assertThat(tree).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .doesNotContain("西域集团", "测试1", "组织架构");

        // 二级 = 锚定节点的子节点
        AnalyticsDepartmentTreeDTO supplyNode = tree.get(0);
        assertThat(supplyNode.getChildren()).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .containsExactly("业务中心");

        // 三级 = 锚定节点的孙节点；第四级截断
        AnalyticsDepartmentTreeDTO bizCenterNode = supplyNode.getChildren().get(0);
        assertThat(bizCenterNode.getChildren()).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .containsExactly("销售一部");
        assertThat(bizCenterNode.getChildren().get(0).getChildren()).isNull();
    }

    @Test
    @DisplayName("锚点0001未命中时回退全树两级 + User兜底（保持现状行为）")
    void shouldFallbackToFullTwoLevelTreeWhenAnchorMissing() {
        OrganizationDepartmentEntity virtualRoot = new OrganizationDepartmentEntity();
        virtualRoot.setDepartmentCode("rootorg");
        virtualRoot.setDepartmentName("组织架构");
        virtualRoot.setParentDepartmentCode(null);

        OrganizationDepartmentEntity l1 = new OrganizationDepartmentEntity();
        l1.setDepartmentCode("D001");
        l1.setDepartmentName("销售总部");
        l1.setParentDepartmentCode("rootorg");

        OrganizationDepartmentEntity l2 = new OrganizationDepartmentEntity();
        l2.setDepartmentCode("D002");
        l2.setDepartmentName("华东区");
        l2.setParentDepartmentCode("D001");

        OrganizationDepartmentEntity l3 = new OrganizationDepartmentEntity();
        l3.setDepartmentCode("D003");
        l3.setDepartmentName("上海办事处");
        l3.setParentDepartmentCode("D002");

        jakarta.persistence.TypedQuery<String> typedQuery = org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of("技术部"));

        when(departmentRepository.findAllByOrderByDepartmentCode())
                .thenReturn(List.of(virtualRoot, l1, l2, l3));

        List<AnalyticsDepartmentTreeDTO> tree = service.fetchDepartmentTree();

        // 回退：全树两级 + User 兜底补根
        assertThat(tree).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .containsExactly("销售总部", "技术部");
        assertThat(tree.get(0).getChildren()).extracting(AnalyticsDepartmentTreeDTO::getLabel)
                .containsExactly("华东区");
        assertThat(tree.get(0).getChildren().get(0).getChildren()).isNull();
    }
}
