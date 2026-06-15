// Input: SystemsExternalMenuController（构造注入 ExternalMenuService）
// Output: MockMvc 测试 — 验证 GET /api/systems/external/menus 返回格式和树结构
// Pos: Test/合约验证
package com.xiyu.bid.systems.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link SystemsExternalMenuController} 的 @WebMvcTest 等效测试.
 *
 * <p>使用 MockMvcBuilders.standaloneSetup 模拟 HTTP 端点，
 * 验证返参格式（success/code/msg/data）和树形结构正确性。</p>
 *
 * <h4>纯核心说明</h4>
 * <ul>
 *   <li>纯核心：菜单数据由 {@link ExternalMenuService#getMenuList()} 提供，本测试仅验证 Controller 契约</li>
 *   <li>副作用：无，Controller 只做数据传递和包转</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SystemsExternalMenuControllerTest {

    @Mock
    private ExternalMenuService menuService;

    @InjectMocks
    private SystemsExternalMenuController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/systems/external/menus 返回 200 + 标准 ApiResponse 结构")
    void getMenus_shouldReturn200WithStandardResponse() throws Exception {
        when(menuService.getMenuList()).thenReturn(dummyMenuList());

        mockMvc.perform(get("/api/systems/external/menus")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("ok"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("data 直接返回菜单树列表，无 systemCode/systemName/menus 包装")
    void getMenus_shouldReturnMenuListDirectly() throws Exception {
        when(menuService.getMenuList()).thenReturn(dummyMenuList());

        mockMvc.perform(get("/api/systems/external/menus")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].id").value("1001"))
                .andExpect(jsonPath("$.data[0].menuName").value("工作台"))
                .andExpect(jsonPath("$.data[0].parentId").value("0"))
                .andExpect(jsonPath("$.data[0].menuCode").value("1001"));
    }

    @Test
    @DisplayName("子菜单包含在父节点的 children 字段中")
    void getMenus_shouldIncludeChildMenus() throws Exception {
        when(menuService.getMenuList()).thenReturn(dummyMenuList());

        mockMvc.perform(get("/api/systems/external/menus")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[1].id").value("1002"))
                .andExpect(jsonPath("$.data[1].menuName").value("标讯中心"))
                .andExpect(jsonPath("$.data[1].children.length()").value(3))
                .andExpect(jsonPath("$.data[1].children[0].menuName").value("标讯列表"))
                .andExpect(jsonPath("$.data[1].children[0].parentId").value("1002"));
    }

    // ── Helper: 构造与 ExternalMenuService 兼容的测试数据 ──

    private static List<ExternalMenuTreeNode> dummyMenuList() {
        ExternalMenuTreeNode dashboard = new ExternalMenuTreeNode(
                "1001", "工作台", "0", "1001", List.of());

        ExternalMenuTreeNode bidding = new ExternalMenuTreeNode(
                "1002", "标讯中心", "0", "1002", List.of(
                new ExternalMenuTreeNode("100201", "标讯列表", "1002", "100201", List.of()),
                new ExternalMenuTreeNode("100202", "新建标讯", "1002", "100202", List.of()),
                new ExternalMenuTreeNode("100203", "关键词订阅", "1002", "100203", List.of())
        ));

        return List.of(dashboard, bidding);
    }
}
