// Input: ExternalMenuService（构造时构建全量菜单树缓存）
// Output: 纯单元测试 — 验证根级数量、parentId/id 约束、子树结构、不可变性
// Pos: Test/纯核心验证
package com.xiyu.bid.systems.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExternalMenuService} 的纯单元测试.
 *
 * <p>菜单数据为程序内固定常量，不依赖 Spring 上下文，可直接构造。
 * 核心验证：根级数量、节点结构约束、子树完整性。</p>
 *
 * <h4>纯核心说明</h4>
 * <ul>
 *   <li>纯核心：{@link ExternalMenuService#getMenus()} 返回值推导</li>
 *   <li>侧写：{@link ExternalMenuTreeNode} 纯数据类（record-like）</li>
 *   <li>无副作用：构造即缓存，getMenus 仅返回不可变引用</li>
 * </ul>
 */
class ExternalMenuServiceTest {

    private ExternalMenuService service;

    @BeforeEach
    void setUp() {
        service = new ExternalMenuService();
    }

    @Test
    @DisplayName("响应包含正确的系统标识和名称")
    void getMenus_shouldReturnCorrectSystemInfo() {
        ExternalMenuResponse response = service.getMenus();

        assertThat(response.getSystemCode()).isEqualTo("bid-platform");
        assertThat(response.getSystemName()).isEqualTo("西域数智化投标管理平台");
        assertThat(response.getMenus()).isNotNull().hasSize(10);
    }

    @Test
    @DisplayName("根级菜单共 10 项，parentId 均为 0")
    void getMenus_shouldHave10RootItemsWithParentIdZero() {
        List<ExternalMenuTreeNode> menus = service.getMenus().getMenus();

        assertThat(menus).hasSize(10);
        assertThat(menus)
                .allSatisfy(node -> {
                    assertThat(node.getParentId()).isEqualTo("0");
                    assertThat(node.getId()).isEqualTo(node.getMenuCode());
                });
    }

    @Test
    @DisplayName("标讯中心包含 3 个子菜单")
    void biddingRootNode_shouldHave3Children() {
        ExternalMenuTreeNode bidding = findRootByMenuCode("1002");

        assertThat(bidding.getMenuName()).isEqualTo("标讯中心");
        assertThat(bidding.getChildren()).hasSize(3);
        assertThat(bidding.getChildren()).extracting(ExternalMenuTreeNode::getMenuName)
                .containsExactly("标讯列表", "新建标讯", "关键词订阅");
    }

    @Test
    @DisplayName("投标项目包含 2 个子菜单")
    void projectRootNode_shouldHave2Children() {
        ExternalMenuTreeNode project = findRootByMenuCode("1003");

        assertThat(project.getMenuName()).isEqualTo("投标项目");
        assertThat(project.getChildren()).hasSize(2);
        assertThat(project.getChildren()).extracting(ExternalMenuTreeNode::getMenuName)
                .containsExactly("项目列表", "创建项目");
    }

    @Test
    @DisplayName("知识库包含 7 个子菜单")
    void knowledgeRootNode_shouldHave7Children() {
        ExternalMenuTreeNode knowledge = findRootByMenuCode("1004");

        assertThat(knowledge.getMenuName()).isEqualTo("知识库");
        assertThat(knowledge.getChildren()).hasSize(7);
        assertThat(knowledge.getChildren()).extracting(ExternalMenuTreeNode::getMenuName)
                .containsExactly("档案台账", "资质库", "人员库", "业绩库", "品牌授权", "案例库", "模板库");
    }

    @Test
    @DisplayName("资源管理包含 7 个子菜单")
    void resourceRootNode_shouldHave7Children() {
        ExternalMenuTreeNode resource = findRootByMenuCode("1005");

        assertThat(resource.getMenuName()).isEqualTo("资源管理");
        assertThat(resource.getChildren()).hasSize(7);
        assertThat(resource.getChildren()).extracting(ExternalMenuTreeNode::getMenuName)
                .containsExactly("资产台账", "保证金管理", "费用管理", "账户管理", "CA 管理", "合同借阅", "结果闭环");
    }

    @Test
    @DisplayName("系统设置包含 5 个子菜单")
    void settingsRootNode_shouldHave5Children() {
        ExternalMenuTreeNode settings = findRootByMenuCode("1010");

        assertThat(settings.getMenuName()).isEqualTo("系统设置");
        assertThat(settings.getChildren()).hasSize(5);
        assertThat(settings.getChildren()).extracting(ExternalMenuTreeNode::getMenuName)
                .containsExactly("组织设置", "组织架构", "流程表单配置", "告警规则", "告警历史");
    }

    @Test
    @DisplayName("无子菜单的根节点：工作台、AI 智能中心、数据分析、操作日志、审计日志")
    void leafRootNodes_shouldHaveEmptyChildren() {
        List.of("1001", "1006", "1007", "1008", "1009").forEach(code -> {
            ExternalMenuTreeNode node = findRootByMenuCode(code);
            assertThat(node.getChildren()).as("节点 %s(%s) 应无子菜单", code, node.getMenuName())
                    .isEmpty();
        });
    }

    @Test
    @DisplayName("子菜单的 parentId 指向其父节点 id")
    void childNodes_shouldHaveParentIdMatchingParentId() {
        ExternalMenuTreeNode bidding = findRootByMenuCode("1002");
        for (ExternalMenuTreeNode child : bidding.getChildren()) {
            assertThat(child.getParentId()).isEqualTo("1002");
        }
    }

    @Test
    @DisplayName("所有子菜单无更深层嵌套（children 为 empty）")
    void childNodes_shouldNotHaveNestedChildren() {
        getRootsWithChildren().forEach(root -> {
            for (ExternalMenuTreeNode child : root.getChildren()) {
                assertThat(child.getChildren())
                        .as("子菜单 %s(%s) 不可包含更深层子节点", child.getId(), child.getMenuName())
                        .isNullOrEmpty();
            }
        });
    }

    @Test
    @DisplayName("返回的响应不可变 —— 多次调用返回相同引用")
    void getMenus_shouldReturnStableReference() {
        ExternalMenuResponse first = service.getMenus();
        ExternalMenuResponse second = service.getMenus();
        assertThat(first).isSameAs(second);
    }

    // ── Helper ──

    private ExternalMenuTreeNode findRootByMenuCode(String menuCode) {
        return service.getMenus().getMenus().stream()
                .filter(n -> n.getMenuCode().equals(menuCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到菜单 code=" + menuCode));
    }

    private List<ExternalMenuTreeNode> getRootsWithChildren() {
        return service.getMenus().getMenus().stream()
                .filter(n -> n.getChildren() != null && !n.getChildren().isEmpty())
                .toList();
    }
}
