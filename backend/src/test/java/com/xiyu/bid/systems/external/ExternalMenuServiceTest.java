// Input: ExternalMenuService（硬编码菜单树）
// Output: 单元测试 — 验证暴露给 OSS 的菜单树包含"任务看板"顶级菜单
// Pos: Test/合约验证
package com.xiyu.bid.systems.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExternalMenuService} 的单元测试.
 *
 * <p>验证暴露给 OSS 的菜单树结构完整性，特别是"任务看板"顶级菜单
 * （code=1011）的存在，这是跨部门协同人员通过 OSS 配置只看任务看板的前提。</p>
 *
 * <h4>纯核心说明</h4>
 * <ul>
 *   <li>纯核心：ExternalMenuService 在构造函数中硬编码菜单树，无外部依赖</li>
 *   <li>副作用：无</li>
 * </ul>
 */
class ExternalMenuServiceTest {

    private final ExternalMenuService service = new ExternalMenuService();

    @Test
    @DisplayName("菜单树包含任务看板顶级菜单（code=1011）")
    void getMenuList_shouldContainTaskBoardTopLevelMenu() {
        List<ExternalMenuTreeNode> menus = service.getMenuList();

        assertThat(menus)
                .extracting(ExternalMenuTreeNode::getMenuCode)
                .contains("1011");

        ExternalMenuTreeNode taskBoard = menus.stream()
                .filter(m -> "1011".equals(m.getMenuCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("任务看板菜单未找到"));

        assertThat(taskBoard.getMenuName()).isEqualTo("任务看板");
        assertThat(taskBoard.getParentId()).isEqualTo("0");
    }

    @Test
    @DisplayName("任务看板作为顶级菜单存在，不挂在其他菜单下")
    void getMenuList_taskBoardShouldBeTopLevel() {
        List<ExternalMenuTreeNode> menus = service.getMenuList();

        ExternalMenuTreeNode taskBoard = menus.stream()
                .filter(m -> "1011".equals(m.getMenuCode()))
                .findFirst()
                .orElseThrow();

        assertThat(taskBoard.getParentId()).isEqualTo("0");
    }
}
