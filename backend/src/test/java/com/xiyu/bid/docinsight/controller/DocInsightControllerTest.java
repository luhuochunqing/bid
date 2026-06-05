package com.xiyu.bid.docinsight.controller;

import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.application.DocumentIntelligenceService;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocInsightController – boundary checks")
class DocInsightControllerTest {

    @Mock
    private DocumentIntelligenceService docInsightService;

    private MockMvc mockMvc;
    private DocInsightController controller;

    /** 合法的 PDF multipart file（1 字节）。 */
    private static final MockMultipartFile VALID_FILE =
            new MockMultipartFile("file", "test.pdf", "application/pdf", "X".getBytes());

    @BeforeEach
    void setUp() {
        controller = new DocInsightController(docInsightService);
        ReflectionTestUtils.setField(controller, "maxUploadMb", 50);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── 400 – empty file ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /parse 空文件返回 400")
    void parse_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(emptyFile)
                        .param("profile", "REPORT")
                        .param("entityId", "42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("上传文件为空或未正确传输"));
    }

    // ── 413 – file too large ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /parse 超过大小上限时返回 413")
    void parse_oversizedFile_returns413() throws Exception {
        // Set limit to 0 MB so any non-empty file exceeds it
        ReflectionTestUtils.setField(controller, "maxUploadMb", 0);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(VALID_FILE)
                        .param("profile", "REPORT")
                        .param("entityId", "42"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.msg").value("上传文件过大"));
    }

    // ── 415 – unsupported content-type ───────────────────────────────────────

    @Test
    @DisplayName("POST /parse 不支持的 Content-Type 返回 415")
    void parse_disallowedContentType_returns415() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(txtFile)
                        .param("profile", "REPORT")
                        .param("entityId", "42"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.msg").value("不支持的文件类型"));
    }

    @Test
    @DisplayName("POST /parse TENDER_INTAKE 允许 text/plain 粘贴文本文件")
    void parse_tenderIntakeTextPlain_returns200() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file", "pasted.txt", "text/plain", "项目名称：西域MRO项目".getBytes());
        DocumentAnalysisResult result = new DocumentAnalysisResult(
                "doc://pasted", Map.of(), List.of(), null, List.of()
        );
        when(docInsightService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(txtFile)
                        .param("profile", "TENDER_INTAKE")
                        .param("entityId", "manual-tender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value("doc://pasted"));
    }

    @Test
    @DisplayName("POST /parse TENDER_INTAKE 允许 .doc Word 文件")
    void parse_tenderIntakeDocFile_returns200() throws Exception {
        MockMultipartFile docFile = new MockMultipartFile(
                "file", "tender.doc", "application/msword", "Word content".getBytes());
        DocumentAnalysisResult result = new DocumentAnalysisResult(
                "doc://tender", Map.of(), List.of(), null, List.of()
        );
        when(docInsightService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(docFile)
                        .param("profile", "TENDER_INTAKE")
                        .param("entityId", "manual-tender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /parse TENDER_INTAKE 允许 .docx Word 文件")
    void parse_tenderIntakeDocxFile_returns200() throws Exception {
        MockMultipartFile docxFile = new MockMultipartFile(
                "file", "tender.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Word content".getBytes());
        DocumentAnalysisResult result = new DocumentAnalysisResult(
                "doc://tender", Map.of(), List.of(), null, List.of()
        );
        when(docInsightService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(docxFile)
                        .param("profile", "TENDER_INTAKE")
                        .param("entityId", "manual-tender"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /parse 50MB 文件应该通过大小检查")
    void parse_fileWithin50MB_returns200() throws Exception {
        // 创建一个接近 50MB 的文件
        byte[] content = new byte[50 * 1024 * 1024]; // 50MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", content);
        DocumentAnalysisResult result = new DocumentAnalysisResult(
                "doc://large", Map.of(), List.of(), null, List.of()
        );
        when(docInsightService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(largeFile)
                        .param("profile", "TENDER_INTAKE")
                        .param("entityId", "manual-tender"))
                .andExpect(status().isOk());
    }

    // ── 400 – invalid profileCode ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /parse profileCode 含非法字符时返回 400")
    void parse_invalidProfileCode_returns400() throws Exception {
        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(VALID_FILE)
                        .param("profile", "!!!INVALID!!!")
                        .param("entityId", "42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("无效的解析配置标识"));
    }

    @Test
    @DisplayName("POST /parse 空白 profileCode 返回 400")
    void parse_blankProfileCode_returns400() throws Exception {
        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(VALID_FILE)
                        .param("profile", "   ")
                        .param("entityId", "42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("无效的解析配置标识"));
    }

    // ── 400 – invalid entityId ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /parse entityId 含非法字符时返回 400")
    void parse_invalidEntityId_returns400() throws Exception {
        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(VALID_FILE)
                        .param("profile", "REPORT")
                        .param("entityId", "<script>alert(1)</script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("无效的实体标识"));
    }

    // ── 403 – project access denied (service throws AccessDeniedException) ────

    @Test
    @DisplayName("POST /parse 服务抛出 AccessDeniedException 时返回 403")
    void parse_accessDenied_returns403() throws Exception {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(docInsightService).process(any(), any(), any());

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(VALID_FILE)
                        .param("profile", "TENDER")
                        .param("entityId", "42"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── 200 – happy path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /parse 有效请求返回 200 和分析结果")
    void parse_validRequest_returns200() throws Exception {
        DocumentAnalysisResult result = new DocumentAnalysisResult(
                "doc://test", Map.of(), List.of(), null, List.of()
        );
        when(docInsightService.process(any(), any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/doc-insight/parse")
                        .file(VALID_FILE)
                        .param("profile", "REPORT")
                        .param("entityId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentId").value("doc://test"));
    }
}
