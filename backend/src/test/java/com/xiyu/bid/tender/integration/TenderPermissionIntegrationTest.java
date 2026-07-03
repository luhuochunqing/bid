package com.xiyu.bid.tender.integration;

import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 标讯关键写接口的权限集成/契约测试。
 *
 * <p>验证 controller 层 @PreAuthorize 收紧后，非授权角色在运行时被正确拒绝。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class TenderPermissionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PUT /api/tenders/{id}: bid_specialist 应返回 403")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void updateTender_byBidSpecialist_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/tenders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "测试标题",
                                  "deadline": "2026-12-31T18:00:00"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/tenders/{id}: 匿名用户应返回 403")
    @WithAnonymousUser
    void updateTender_byAnonymous_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/tenders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "测试标题",
                                  "deadline": "2026-12-31T18:00:00"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/tenders/{id}/transfer: bid_specialist 应返回 403")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void transferTender_byBidSpecialist_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerId": 2
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/tenders/{id}/transfer: sales 应返回 403")
    @WithMockUser(username = "sales", roles = {"BID_PROJECTLEADER"})
    void transferTender_bySales_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerId": 2
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/tenders/{id}/transfer: staff 应返回 403")
    @WithMockUser(username = "bid_specialist", roles = {"MANAGER"})
    void transferTender_byStaff_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerId": 2
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/tenders/{id}/transfer: 匿名用户应返回 403")
    @WithAnonymousUser
    void transferTender_byAnonymous_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerId": 2
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/tenders: bid_specialist 应返回 200")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void listTenders_byBidSpecialist_returnsOk() throws Exception {
        mockMvc.perform(get("/api/tenders").param("page", "0").param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/tenders/{id}: bid_specialist 应放行（资源不存在时 404 而非 403）")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void getTenderById_byBidSpecialist_isAllowed() throws Exception {
        mockMvc.perform(get("/api/tenders/1"))
                // 权限放行后，测试库无该记录 → 业务层返回 404；若被 @PreAuthorize 拦截则是 403
                .andExpect(status().isNotFound());
    }

    // ====================================================================
    // 2.1 标讯列表契约测试补充（飞书《标讯中心·权限矩阵》V1.0）
    // 锁定角色准入，防止 Controller 注解未来被改宽/改窄
    // ====================================================================

    @Test
    @DisplayName("2.1.5 DELETE /api/tenders/{id}: bid_specialist 应返回 403（文档：投标专员不可删除）")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void deleteTender_byBidSpecialist_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/tenders/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1.1 GET /api/tenders: 跨部门协同人员（bid-otherDept）应返回 403（文档：不涉及标讯模块）")
    @WithMockUser(username = "bid-otherDept", roles = {"BID_OTHERDEPT"})
    void listTenders_byBidOtherDept_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/tenders").param("page", "0").param("size", "20"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1.1 GET /api/tenders: 行政人员（bid-administration）应返回 403（文档：不涉及标讯模块）")
    @WithMockUser(username = "bid-administration", roles = {"BID_ADMINISTRATION"})
    void listTenders_byBidAdministration_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/tenders").param("page", "0").param("size", "20"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.1.1 GET /api/tenders: 投标项目负责人（bid-projectLeader）应返回 200（文档：仅自己的，由 Service 层过滤）")
    @WithMockUser(username = "projectLeader", roles = {"BID_PROJECTLEADER"})
    void listTenders_byProjectLeader_returnsOk() throws Exception {
        mockMvc.perform(get("/api/tenders").param("page", "0").param("size", "20"))
                .andExpect(status().isOk());
    }

    // ====================================================================
    // 2.2 标讯录入契约测试（飞书《标讯中心·权限矩阵》2.2）
    // 文档：手动录入含项目负责人；批量导入/下载模板不含项目负责人
    // ====================================================================

    @Test
    @DisplayName("2.2 手动录入 POST /api/tenders: 投标项目负责人应放行（文档：允许录入，业务层返回非 403）")
    @WithMockUser(username = "projectLeader", roles = {"BID_PROJECTLEADER"})
    void createTender_byProjectLeader_notForbidden() throws Exception {
        // 项目负责人应能进入录入端点；业务校验（如必填字段缺失）返回 4xx，但权限层不是 403
        int status = mockMvc.perform(post("/api/tenders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "测试标讯", "deadline": "2026-12-31T18:00:00" }
                                """))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    @DisplayName("2.2 批量导入 POST /api/tenders/import: 投标项目负责人应返回 403（文档：批量导入不含项目负责人）")
    @WithMockUser(username = "projectLeader", roles = {"BID_PROJECTLEADER"})
    void importTenders_byProjectLeader_returnsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/tenders/import").file("file", "test".getBytes()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.2 下载模板 GET /api/tenders/import-template: 投标项目负责人应返回 403（文档：不含项目负责人）")
    @WithMockUser(username = "projectLeader", roles = {"BID_PROJECTLEADER"})
    void downloadTemplate_byProjectLeader_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/tenders/import-template"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.2 批量导入 POST /api/tenders/import: 投标专员应放行（文档：专员可批量导入，权限层非 403）")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void importTenders_byBidTeam_notForbidden() throws Exception {
        int status = mockMvc.perform(multipart("/api/tenders/import").file("file", "test".getBytes()))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
    }

    // ====================================================================
    // 2.3 标讯评估契约测试（飞书《标讯中心·权限矩阵》2.3）
    // 锁定"确认投标/放弃投标"的角色准入——4 个重叠端点的权限差异显性化
    // ====================================================================

    @Test
    @DisplayName("2.3 确认投标 路径A POST /api/tenders/{id}/participate: 投标专员应 403（文档：仅管理员/组长）")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void participateBid_byBidTeam_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/participate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 确认投标 路径A POST /api/tenders/{id}/participate: 投标项目负责人应 403（文档：仅管理员/组长）")
    @WithMockUser(username = "projectLeader", roles = {"BID_PROJECTLEADER"})
    void participateBid_byProjectLeader_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/participate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 放弃投标 路径A POST /api/tenders/{id}/abandon: 投标专员应 403")
    @WithMockUser(username = "bid-specialist", roles = {"BID_TEAM"})
    void abandonBid_byBidTeam_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/tenders/1/abandon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"测试\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 审核标讯 路径B POST /api/tenders/{id}/review: 投标组长应 403（⚠️ 注解仅 ADMIN，与文档'管理员/组长'不符）")
    @WithMockUser(username = "bid-TeamLeader", roles = {"BID_TEAMLEADER"})
    void reviewTender_byBidTeamLeader_returnsForbidden_currentImpl() throws Exception {
        // ⚠️ 现状锁定：reviewTender 注解仅 hasAnyRole('ADMIN')，组长被拒。
        // 文档"确认投标"要求管理员+组长，但此端点注解过严。待业务确认是否统一 4 端点。
        mockMvc.perform(post("/api/tenders/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.3 确认投标 路径B POST /api/tenders/{id}/bid: 投标项目负责人（MANAGER）应放行（⚠️ 注解含 MANAGER，与文档'仅管理员/组长'不符）")
    @WithMockUser(username = "projectLeader", roles = {"MANAGER"})
    void proceedToBid_byManager_notForbidden_currentImpl() throws Exception {
        // ⚠️ 现状锁定：proceedToBid 注解 hasAnyRole('ADMIN','MANAGER')，MANAGER（含项目负责人）被放行。
        // 文档"确认投标"仅管理员/组长，但此端点放行了 MANAGER。待业务确认是否收紧。
        int status = mockMvc.perform(post("/api/tenders/1/bid"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
    }
}
