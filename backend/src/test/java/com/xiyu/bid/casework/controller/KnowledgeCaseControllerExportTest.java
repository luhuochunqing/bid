package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.CaseExportExcelAppService;
import com.xiyu.bid.casework.application.CasePrecipitationAppService;
import com.xiyu.bid.casework.application.service.CaseExportAppService;
import com.xiyu.bid.casework.application.service.CaseReferenceAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseCommandAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseQueryAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseRecommendAppService;
import com.xiyu.bid.casework.domain.model.CaseExportResult;
import com.xiyu.bid.service.ProjectAccessScopeService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeCaseController.class,
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
class KnowledgeCaseControllerExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private KnowledgeCaseQueryAppService queryAppService;
    @MockBean private KnowledgeCaseCommandAppService commandAppService;
    @MockBean private KnowledgeCaseRecommendAppService recommendAppService;
    @MockBean private CaseReferenceAppService caseReferenceAppService;
    @MockBean private CasePrecipitationAppService precipitationAppService;
    @MockBean private CaseExportAppService caseExportZipAppService;
    @MockBean private CaseExportExcelAppService caseExportExcelAppService;
    @MockBean private ProjectAccessScopeService projectAccessScopeService;

    @Test
    @WithMockUser(roles = "STAFF")
    void exportExcel_ReturnsOctetStream() throws Exception {
        when(caseExportExcelAppService.exportCasesAsExcel(
                any(), any(), any(), any(),
                any(), any(), any(), any(), any()))
                .thenReturn(new CaseExportExcelAppService.ExportResult(
                        new byte[]{1, 2, 3}, "test.xlsx", 1));

        mockMvc.perform(post("/api/cases/export-excel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.xlsx\""));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void exportZip_ReturnsZip() throws Exception {
        when(caseExportZipAppService.exportCases(any(), any()))
                .thenReturn(new CaseExportResult(
                        new byte[]{0x50, 0x4B, 0x03, 0x04}, "cases.zip", 2, 100));

        mockMvc.perform(post("/api/cases/export-zip").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/zip")))
                .andExpect(header().exists("Content-Disposition"));
    }
}
