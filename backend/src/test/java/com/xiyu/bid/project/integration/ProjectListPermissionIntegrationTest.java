package com.xiyu.bid.project.integration;

import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2.1 项目列表权限契约测试（飞书《投标项目·权限矩阵》2.1）。
 *
 * <p>锁定项目列表 + 导出的角色准入。列表 getAllProjects 用反射锁定 isAuthenticated 注解
 * （已有 ProjectControllerAuthorizationTest 覆盖，此处补导出端点的角色差距）。
 *
 * <p>数据范围细节由 ProjectControllerAccessIntegrationTest（种子数据集成）+
 * ProjectAccessScopeServiceTest（Service 单测）覆盖，本测试不重复。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectListPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ====================================================================
    // 2.1 导出 GET /api/projects/export：hasAnyRole('ADMIN','MANAGER')
    // ⚠️ Gap：文档"投标专员：✅ 可见范围 导出"，但代码不含 BID_TEAM
    //
    // 注：列表 getAllProjects 的 isAuthenticated 注解已由
    // ProjectControllerAuthorizationTest.getAllProjects_shouldBeAccessibleToAuthenticatedUsers
    // 锁定，数据范围由 ProjectControllerAccessIntegrationTest + ProjectAccessScopeServiceTest 覆盖。
    // 本测试聚焦导出端点的角色差距。
    // ====================================================================

    @Test
    @DisplayName("2.1 项目导出：投标项目负责人应放行（MANAGER 含 sales）")
    @WithMockUser(username = "manager-user", roles = {"MANAGER"})
    void exportProjects_byManager_returnsOk() throws Exception {
        // MANAGER 进入端点（具体导出内容按 dataScope 过滤）
        int status = mockMvc.perform(get("/api/projects/export"))
                .andReturn().getResponse().getStatus();
        // 200 或 500（若导出逻辑依赖 DB），但不应是 403
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    @DisplayName("2.1 项目导出：投标专员应 403（⚠️ Gap：文档允许可见范围导出，但代码 hasAnyRole 不含 BID_TEAM）")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void exportProjects_byBidTeam_returnsForbidden_gap() throws Exception {
        // ⚠️ 现状锁定：export 注解 hasAnyRole('ADMIN','MANAGER')，BID_TEAM 不持 MANAGER → 403。
        // 文档 2.1 "投标专员：✅ 可见范围 导出"，但代码拦截。待业务确认是否放行投标专员导出。
        mockMvc.perform(get("/api/projects/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1 项目导出：行政人员应 403（文档：不涉及项目模块）")
    @WithMockUser(username = "bid-administration", roles = {"BID_ADMINISTRATION"})
    void exportProjects_byBidAdministration_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/projects/export"))
                .andExpect(status().isForbidden());
    }
}
