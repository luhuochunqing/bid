package com.xiyu.bid.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.integration.application.WeComIntegrationAppService;
import com.xiyu.bid.integration.dto.WeComConnectivityResponse;
import com.xiyu.bid.integration.dto.WeComIntegrationRequest;
import com.xiyu.bid.integration.dto.WeComIntegrationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WeComIntegrationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WeComIntegrationController — REST endpoints")
class WeComIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WeComIntegrationAppService appService;

    // ---- GET ----

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET returns 200 with ApiResponse envelope")
    void get_returns200() throws Exception {
        WeComIntegrationResponse response = WeComIntegrationResponse.configured(
                "wwcorp", "1000001", false, false, null);
        when(appService.getConfig()).thenReturn(response);

        mockMvc.perform(get("/api/admin/integrations/wecom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.corpId").value("wwcorp"))
                .andExpect(jsonPath("$.data.secretConfigured").value(true))
                .andExpect(jsonPath("$.data.corpSecret").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET returns secretConfigured=false when not configured")
    void get_notConfigured() throws Exception {
        when(appService.getConfig()).thenReturn(WeComIntegrationResponse.empty());

        mockMvc.perform(get("/api/admin/integrations/wecom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.secretConfigured").value(false));
    }

    // ---- PUT ----

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    @DisplayName("PUT with valid body returns 200")
    void put_validBody_returns200() throws Exception {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "wwcorp", "1000001", "secret", false, false, null);
        WeComIntegrationResponse response = WeComIntegrationResponse.configured(
                "wwcorp", "1000001", false, false, null);
        when(appService.saveConfig(any(), anyString())).thenReturn(response);

        mockMvc.perform(put("/api/admin/integrations/wecom")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(appService).saveConfig(any(WeComIntegrationRequest.class), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT with blank corpId returns 400")
    void put_blankCorpId_returns400() throws Exception {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "", "1000001", "secret", false, false, null);

        mockMvc.perform(put("/api/admin/integrations/wecom")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT with blank corpSecret returns 400")
    void put_blankCorpSecret_returns400() throws Exception {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "wwcorp", "1000001", "", false, false, null);

        mockMvc.perform(put("/api/admin/integrations/wecom")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    @DisplayName("PUT service throws IllegalArgumentException returns 400")
    void put_serviceThrowsIAE_returns400() throws Exception {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "wwcorp", "1000001", "secret", false, false, null);
        when(appService.saveConfig(any(), anyString()))
                .thenThrow(new IllegalArgumentException("domain validation failed"));

        mockMvc.perform(put("/api/admin/integrations/wecom")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---- POST /test ----

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /test returns connectivity result in ApiResponse")
    void postTest_returnsConnectivityResult() throws Exception {
        WeComConnectivityResponse connectivity = new WeComConnectivityResponse(
                true, "连接成功", LocalDateTime.now());
        when(appService.testConnectivity()).thenReturn(connectivity);

        mockMvc.perform(post("/api/admin/integrations/wecom/test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.message").value("连接成功"));
    }
}
