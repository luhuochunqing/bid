package com.xiyu.bid.admin.permissions.controller;

import com.xiyu.bid.admin.permissions.application.EndpointPermissionCatalogAppService;
import com.xiyu.bid.admin.permissions.dto.EndpointPermissionItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEndpointPermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EndpointPermissionCatalogAppService catalogAppService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListEndpointPermissionMatrix() throws Exception {
        when(catalogAppService.listEndpointPermissions()).thenReturn(List.of(
                new EndpointPermissionItem(
                        "GET",
                        "/api/alerts/history/unresolved",
                        "alerts",
                        "AlertHistoryController",
                        "getUnresolvedAlertHistories",
                        "hasAnyRole('ADMIN', 'MANAGER')",
                        List.of("ADMIN", "MANAGER"),
                        "ADMIN_MANAGER",
                        "MEDIUM",
                        false,
                        "METHOD_PRE_AUTHORIZE",
                        "入口层权限矩阵；不展开 Service 内部 hasAuthority 等二次授权"
                )
        ));

        mockMvc.perform(get("/api/admin/permissions/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].path").value("/api/alerts/history/unresolved"))
                .andExpect(jsonPath("$.data[0].allowedRoles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].configurable").value(false))
                .andExpect(jsonPath("$.data[0].scopeNote").value(org.hamcrest.Matchers.containsString("入口层权限")));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotListEndpointPermissionMatrix() throws Exception {
        mockMvc.perform(get("/api/admin/permissions/endpoints"))
                .andExpect(status().isForbidden());
    }
}
