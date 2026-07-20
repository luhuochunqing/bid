package com.xiyu.bid.warehouse.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.warehouse.application.WarehouseExportAppService;
import com.xiyu.bid.warehouse.application.WarehouseLedgerExportAppService;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.controller.UserResolver;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @TempDir
    Path tempDir;

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

    // ========== downloadExportFile 流式下载端点测试 ==========

    @Test
    @DisplayName("下载端点：任务不存在 → 404 Not Found")
    void downloadExportFile_taskNotFound_returns404() throws Exception {
        when(exportAppService.getExportFile(eq(999L), eq(1L)))
                .thenThrow(new IllegalArgumentException("导出任务不存在或无权限"));

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/999/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("下载端点：任务未完成 → 400 Bad Request")
    void downloadExportFile_notCompleted_returns400() throws Exception {
        when(exportAppService.getExportFile(eq(1L), eq(1L)))
                .thenThrow(new IllegalStateException("导出任务尚未完成"));

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/1/download"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("下载端点：文件已过期 → 400 Bad Request")
    void downloadExportFile_expired_returns400() throws Exception {
        when(exportAppService.getExportFile(eq(1L), eq(1L)))
                .thenThrow(new IllegalStateException("导出文件已过期"));

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/1/download"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("下载端点：文件已被清理 → 400 Bad Request")
    void downloadExportFile_fileCleaned_returns400() throws Exception {
        when(exportAppService.getExportFile(eq(1L), eq(1L)))
                .thenThrow(new IllegalStateException("导出文件已被清理"));

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/1/download"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("下载端点：IO 异常 → 500 Internal Server Error")
    void downloadExportFile_ioError_returns500() throws Exception {
        when(exportAppService.getExportFile(eq(1L), eq(1L)))
                .thenThrow(new IOException("磁盘读取失败"));

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/1/download"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("下载端点：正常下载 → 200 OK + 正确响应头")
    void downloadExportFile_success_returnsOkWithHeaders() throws Exception {
        Path zipFile = tempDir.resolve("test-export.zip");
        byte[] content = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        Files.write(zipFile, content);

        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .id(1L)
                .status(WarehouseExportTaskEntity.ExportStatus.COMPLETED)
                .completedAt(LocalDateTime.of(2026, 7, 18, 10, 30, 0))
                .build();

        when(exportAppService.getExportFile(eq(1L), eq(1L))).thenReturn(zipFile);
        when(exportAppService.getTaskStatus(eq(1L), eq(1L))).thenReturn(task);

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length)))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/zip"));
    }

    @Test
    @DisplayName("下载端点：未登录 → 401 Unauthorized")
    void downloadExportFile_notLoggedIn_returns401() throws Exception {
        when(userResolver.resolveCurrentUserId()).thenReturn(null);

        mockMvc.perform(get("/api/knowledge/warehouses/export/tasks/1/download"))
                .andExpect(status().isUnauthorized());
    }
}
