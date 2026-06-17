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
    @WithMockUser(username = "bid-specialist", roles = {"BID_SPECIALIST"})
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
    @WithMockUser(username = "bid-specialist", roles = {"BID_SPECIALIST"})
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
    @WithMockUser(username = "sales", roles = {"SALES"})
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
    @WithMockUser(username = "staff", roles = {"STAFF"})
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
}
