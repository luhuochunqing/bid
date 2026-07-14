package com.xiyu.bid.warehouse.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.warehouse.application.WarehouseExportAppService;
import com.xiyu.bid.warehouse.application.WarehouseLedgerExportAppService;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WarehouseExportController triggerExport 契约测试（CO-582 §3.1）。
 * 重点覆盖 parseAttachmentForms 的默认值、双选、空数组、非法值四种场景。
 */
class WarehouseExportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private WarehouseExportAppService exportAppService;
    private WarehouseLedgerExportAppService ledgerExportAppService;
    private UserResolver userResolver;

    @BeforeEach
    void setUp() {
        exportAppService = mock(WarehouseExportAppService.class);
        ledgerExportAppService = mock(WarehouseLedgerExportAppService.class);
        userResolver = mock(UserResolver.class);

        when(userResolver.resolveCurrentUserId()).thenReturn(1L);
        when(userResolver.resolveCurrentOperatorLabel()).thenReturn("admin");
        when(exportAppService.export(any(), any(), any(), any(), any()))
                .thenReturn(new WarehouseExportAppService.ExportTaskResult(100L));

        WarehouseExportController controller = new WarehouseExportController(
                exportAppService, ledgerExportAppService, userResolver, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("body=null → 默认 [WORD_COMBINED]，返回 202")
    void triggerExport_nullBody_defaultsToWordCombined() throws Exception {
        mockMvc.perform(post("/api/knowledge/warehouses/export"))
                .andExpect(status().isAccepted());

        verify(exportAppService).export(any(), eq(1L), any(), any(),
                eq(Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED)));
    }

    @Test
    @DisplayName("body.attachmentForms=[WORD_COMBINED] → 单选 Word，返回 202")
    void triggerExport_wordCombinedOnly_returnsAccepted() throws Exception {
        String body = "{\"attachmentForms\":[\"WORD_COMBINED\"]}";

        mockMvc.perform(post("/api/knowledge/warehouses/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(exportAppService).export(any(), eq(1L), any(), any(),
                eq(Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED)));
    }

    @Test
    @DisplayName("body.attachmentForms=[ATTACHMENTS_FOLDER,WORD_COMBINED] → 双选，返回 202")
    void triggerExport_bothForms_returnsAccepted() throws Exception {
        String body = "{\"attachmentForms\":[\"ATTACHMENTS_FOLDER\",\"WORD_COMBINED\"]}";

        mockMvc.perform(post("/api/knowledge/warehouses/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(exportAppService).export(any(), eq(1L), any(), any(),
                eq(Set.of(WarehouseAttachmentOrganizationForm.ATTACHMENTS_FOLDER,
                        WarehouseAttachmentOrganizationForm.WORD_COMBINED)));
    }

    @Test
    @DisplayName("body.attachmentForms=[] → 空数组 → 400 Bad Request")
    void triggerExport_emptyFormsArray_returns400() throws Exception {
        String body = "{\"attachmentForms\":[]}";

        mockMvc.perform(post("/api/knowledge/warehouses/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("body.attachmentForms=[\"INVALID\"] → 非法值 → 400 Bad Request")
    void triggerExport_invalidFormName_returns400() throws Exception {
        String body = "{\"attachmentForms\":[\"INVALID\"]}";

        mockMvc.perform(post("/api/knowledge/warehouses/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("body.attachmentForms 缺省 → 默认 [WORD_COMBINED]（CO-582 §3.1）")
    void triggerExport_missingAttachmentFormsField_defaultsToWordCombined() throws Exception {
        String body = "{\"scope\":\"filter\"}";

        mockMvc.perform(post("/api/knowledge/warehouses/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(exportAppService).export(any(), eq(1L), any(), any(),
                eq(Set.of(WarehouseAttachmentOrganizationForm.WORD_COMBINED)));
    }

    @Test
    @DisplayName("body.attachmentForms 类型非法 → 400 Bad Request")
    void triggerExport_nonListAttachmentForms_returns400() throws Exception {
        String body = "{\"attachmentForms\":\"WORD_COMBINED\"}";

        mockMvc.perform(post("/api/knowledge/warehouses/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
