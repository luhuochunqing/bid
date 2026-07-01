// Input: MarginController export endpoint
// Output: Controller-layer test verifying Excel export response headers and body
// Pos: Test/Controller切片验证
package com.xiyu.bid.resources.controller;

import com.xiyu.bid.resources.service.MarginExportService;
import com.xiyu.bid.resources.service.MarginService;
import com.xiyu.bid.security.CurrentUserResolver;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MarginController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.xiyu.bid.config.SecurityConfig.class,
                        com.xiyu.bid.auth.JwtAuthenticationFilter.class,
                        com.xiyu.bid.config.RateLimitFilter.class,
                        com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter.class
                }
        ))
@AutoConfigureMockMvc(addFilters = false)
class MarginControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private MarginService marginService;
    @MockBean private MarginExportService marginExportService;
    @MockBean private CurrentUserResolver currentUserResolver;

    @Test
    @WithMockUser(roles = "MANAGER")
    void export_ReturnsExcelContentType() throws Exception {
        when(marginExportService.exportToExcel(any(), anyString(), any()))
                .thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/resource/margin/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void export_WithFilters_ReturnsExcel() throws Exception {
        when(marginExportService.exportToExcel(any(), anyString(), any()))
                .thenReturn(new byte[]{0x50, 0x4B, 0x03, 0x04});

        mockMvc.perform(get("/api/resource/margin/export")
                        .param("projectName", "测试项目")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().exists("Content-Disposition"));
    }
}
