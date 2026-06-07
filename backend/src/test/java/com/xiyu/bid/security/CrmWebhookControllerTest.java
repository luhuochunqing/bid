package com.xiyu.bid.security;

import com.xiyu.bid.dto.webhook.CrmPermissionWebhookPayload;
import com.xiyu.bid.security.service.CrmPermissionSyncService;
import com.xiyu.bid.security.service.WebhookTokenAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "webhook.crm.token=test-crm-secret")
class CrmWebhookControllerTest {

    private static final String VALID_PAYLOAD = """
            {
              "customerId": "CRM_001",
              "permissions": [
                { "userId": 101, "permissionType": "OWNER" },
                { "userId": 102, "permissionType": "SHARING" }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CrmPermissionSyncService syncService;

    @Test
    void syncPermissions_WithValidToken_ReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/webhooks/crm/permissions")
                        .header(WebhookTokenAuthenticator.CRM_HEADER, "test-crm-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void syncPermissions_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/webhooks/crm/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verify(syncService, never()).syncCustomerPermissions(any(CrmPermissionWebhookPayload.class));
    }

    @Test
    void syncPermissions_WithWrongToken_Returns401() throws Exception {
        mockMvc.perform(post("/api/webhooks/crm/permissions")
                        .header(WebhookTokenAuthenticator.CRM_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verify(syncService, never()).syncCustomerPermissions(any(CrmPermissionWebhookPayload.class));
    }
}

