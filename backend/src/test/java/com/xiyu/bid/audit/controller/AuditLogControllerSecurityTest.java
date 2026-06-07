package com.xiyu.bid.audit.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void queryAuditLogs_isAdminOrAuditorOnly() throws NoSuchMethodException {
        Method method = AuditLogController.class.getMethod(
                "getAuditLogs",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('ADMIN', 'AUDITOR')");
    }

    @Test
    @WithMockUser(username = "manager", roles = {"MANAGER"})
    void managerCannotQueryAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void staffCannotQueryAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "auditor", roles = {"AUDITOR"})
    void auditorCanQueryAuditLogs() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "staff", roles = {"STAFF"})
    void staffCanQueryOwnOperationLogs() throws Exception {
        mockMvc.perform(get("/api/audit/my"))
                .andExpect(status().isOk());
    }
}
