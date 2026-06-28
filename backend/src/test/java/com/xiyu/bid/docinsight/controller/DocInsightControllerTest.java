package com.xiyu.bid.docinsight.controller;

import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.application.DocumentIntelligenceService;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocInsightController – boundary checks")
class DocInsightControllerTest {

    @Mock
    private DocumentIntelligenceService docInsightService;

    private MockMvc mockMvc;
    private DocInsightController controller;

    @TempDir
    Path tempUploadDir;

    /** 合法的 PDF multipart file（1 字节）。 */
    private static final MockMultipartFile VALID_FILE =
            new MockMultipartFile("file", "test.pdf", "application/pdf", "X".getBytes());

    @BeforeEach
    void setUp() {
        controller = new DocInsightController(docInsightService);
        ReflectionTestUtils.setField(controller, "maxUploadMb", 50);
        ReflectionTestUtils.setField(controller, "configuredUploadDir", tempUploadDir.toString());

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

    // ── /download – 本地文件下载（doc-insight://）─────────────────────────────

    @Test
    @DisplayName("GET /download doc-insight:// 本地文件存在时返回 200 和文件流")
    void download_localFileExists_returns200() throws Exception {
        // 准备本地文件
        Path file = tempUploadDir.resolve("TENDER_INTAKE").resolve("abc123-test.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, "test content".getBytes());

        String fileUrl = "doc-insight://TENDER_INTAKE/abc123-test.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"test.pdf\"; filename*=UTF-8''test.pdf"))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("GET /download doc-insight:// 本地文件不存在时返回 404")
    void download_localFileNotExists_returns404() throws Exception {
        String fileUrl = "doc-insight://TENDER_INTAKE/nonexistent.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /download 路径遍历攻击（..）返回 400")
    void download_pathTraversal_returns400() throws Exception {
        String fileUrl = "doc-insight://../../../etc/passwd";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /download 无效 URL 格式（非 doc-insight:// 也非 http(s)://）返回 400")
    void download_invalidUrlFormat_returns400() throws Exception {
        String fileUrl = "ftp://example.com/file.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("无效的文件 URL 格式"));
    }

    @Test
    @DisplayName("GET /download 空 fileUrl 返回 400")
    void download_emptyFileUrl_returns400() throws Exception {
        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /download doc-insight:// 路径越界返回 400")
    void download_localPathEscapesUploadRoot_returns400() throws Exception {
        // 构造一个能绕过 startsWith 检查但实际越界的路径
        String fileUrl = "doc-insight://TENDER_INTAKE/../../../etc/passwd";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(status().isBadRequest());
    }

    // ── /download – 外部 URL 代理下载（http(s)://）────────────────────────────
    // 注意：代理下载涉及真实 HTTP 请求，无法在单元测试中验证完整流程。
    // 这里只验证 URL 路由逻辑：http(s):// URL 不会返回 400 "无效的文件 URL 格式"。
    // 完整的代理下载验证在服务器集成测试中完成。

    @Test
    @DisplayName("GET /download https:// URL 路由到代理下载（不返回 400 无效格式）")
    void download_httpsUrl_doesNotReturnInvalidFormat() throws Exception {
        String fileUrl = "https://example.com/nonexistent/file.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 400) {
                        throw new AssertionError(
                                "https:// URL 不应返回 400 无效格式，实际: " + status +
                                " msg: " + result.getResponse().getContentAsString());
                    }
                });
    }

    @Test
    @DisplayName("GET /download http:// URL 也路由到代理下载")
    void download_httpUrl_alsoRoutesToProxyDownload() throws Exception {
        String fileUrl = "http://example.com/nonexistent/file.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 400) {
                        throw new AssertionError(
                                "http:// URL 不应返回 400 无效格式，实际: " + status);
                    }
                });
    }

    @Test
    @DisplayName("GET /download https:// URL 返回 502 BAD_GATEWAY 当外部服务器不可达")
    void download_externalUrlUnreachable_returns502() throws Exception {
        // 使用非私有地址的不可达端口（SSRF 防护会放行公网 IP，但连接会失败 → 502）
        String fileUrl = "https://192.0.2.1:12345/nonexistent/file.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != HttpStatus.NOT_FOUND.value() && status != HttpStatus.BAD_GATEWAY.value()) {
                        throw new AssertionError("外部 URL 应返回 404 或 502，实际: " + status);
                    }
                });
    }

    @Test
    @DisplayName("GET /download 外部 URL 指向 localhost 时返回 400（SSRF 防护）")
    void download_externalUrlLocalhost_returns400() throws Exception {
        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", "http://localhost:8080/admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /download 外部 URL 指向私有 IP 时返回 400（SSRF 防护）")
    void download_externalUrlPrivateIp_returns400() throws Exception {
        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", "http://10.0.0.1/admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /download doc-insight:// 中文文件名返回正确的 Content-Disposition")
    void download_chineseFilename_returnsCorrectContentDisposition() throws Exception {
        // 准备中文文件名的本地文件
        Path file = tempUploadDir.resolve("TENDER_INTAKE").resolve("abc123-招标公告及要求.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, "test content".getBytes());

        String fileUrl = "doc-insight://TENDER_INTAKE/abc123-招标公告及要求.pdf";

        mockMvc.perform(get("/api/doc-insight/download").param("fileUrl", fileUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"招标公告及要求.pdf\"; filename*=UTF-8''%E6%8B%9B%E6%A0%87%E5%85%AC%E5%91%8A%E5%8F%8A%E8%A6%81%E6%B1%82.pdf"))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("extractFileNameFromPath 正确提取普通文件名")
    void extractFileNameFromPath_regularFilename() {
        assertThat(extractFileNameFromPath("/path/to/file.pdf")).isEqualTo("file.pdf");
        assertThat(extractFileNameFromPath("/file.pdf")).isEqualTo("file.pdf");
        assertThat(extractFileNameFromPath("file.pdf")).isEqualTo("file.pdf");
    }

    @Test
    @DisplayName("extractFileNameFromPath URL 解码中文文件名")
    void extractFileNameFromPath_urlEncodedChinese() {
        assertThat(extractFileNameFromPath("/path/%E6%A0%87%E8%AE%AF%E6%96%87%E4%BB%B6.pdf"))
                .isEqualTo("标讯文件.pdf");
    }

    @Test
    @DisplayName("extractFileNameFromPath 路径为空时返回 attachment")
    void extractFileNameFromPath_emptyPath_returnsAttachment() {
        assertThat(extractFileNameFromPath(null)).isEqualTo("attachment");
        assertThat(extractFileNameFromPath("")).isEqualTo("attachment");
        assertThat(extractFileNameFromPath("/")).isEqualTo("attachment");
    }

    @Test
    @DisplayName("extractFileNameFromPath 解码失败时返回原始名称")
    void extractFileNameFromPath_decodeFailure_returnsOriginal() {
        assertThat(extractFileNameFromPath("/path/%XXinvalid")).isEqualTo("%XXinvalid");
    }

    private String extractFileNameFromPath(String path) {
        return ReflectionTestUtils.invokeMethod(controller, "extractFileNameFromPath", path);
    }
}
