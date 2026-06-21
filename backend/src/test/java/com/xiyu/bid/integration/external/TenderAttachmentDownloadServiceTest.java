package com.xiyu.bid.integration.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TenderAttachmentDownloadService 单元测试（CO-280 403 修复）。
 * 覆盖 doc-insight:// 本地下载 + http(s):// 外部代理下载 + 边界校验。
 */
@DisplayName("TenderAttachmentDownloadService – CRM 集成附件下载")
class TenderAttachmentDownloadServiceTest {

    private TenderAttachmentDownloadService service;

    @TempDir
    Path tempUploadDir;

    @BeforeEach
    void setUp() {
        service = new TenderAttachmentDownloadService();
        ReflectionTestUtils.setField(service, "configuredUploadDir", tempUploadDir.toString());
    }

    // ── doc-insight:// 本地下载 ────────────────────────────────────────────────

    @Test
    @DisplayName("doc-insight:// 本地文件存在时返回 200 和文件流")
    void download_localFileExists_returns200() throws IOException {
        Path file = tempUploadDir.resolve("TENDER_INTAKE/abc123-test.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test content");

        String fileUrl = "doc-insight://TENDER_INTAKE/abc123-test.pdf";
        ResponseEntity<Resource> response = service.download(fileUrl);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().toString())
                .contains("attachment");
        assertThat(response.getHeaders().getFirst("Content-Type"))
                .isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("doc-insight:// 本地文件不存在时返回 404")
    void download_localFileNotExists_returns404() {
        String fileUrl = "doc-insight://TENDER_INTAKE/nonexistent.pdf";
        assertThatThrownBy(() -> service.download(fileUrl))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(404));
    }

    @Test
    @DisplayName("doc-insight:// 路径遍历攻击（..）返回 400")
    void download_pathTraversal_returns400() {
        String fileUrl = "doc-insight://../../../etc/passwd";
        assertThatThrownBy(() -> service.download(fileUrl))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("doc-insight:// 路径越界返回 400")
    void download_localPathEscapesUploadRoot_returns400() {
        String fileUrl = "doc-insight://TENDER_INTAKE/../../../etc/passwd";
        assertThatThrownBy(() -> service.download(fileUrl))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("doc-insight:// 中文文件名返回正确的 Content-Disposition")
    void download_chineseFilename_returnsCorrectContentDisposition() throws IOException {
        Path file = tempUploadDir.resolve("TENDER_INTAKE/abc123-招标公告.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "中文内容");

        String fileUrl = "doc-insight://TENDER_INTAKE/abc123-招标公告.pdf";
        ResponseEntity<Resource> response = service.download(fileUrl);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String disposition = response.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains("filename*=UTF-8''");
    }

    // ── 参数校验 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("空 fileUrl 返回 400")
    void download_emptyFileUrl_returns400() {
        assertThatThrownBy(() -> service.download(""))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("null fileUrl 返回 400")
    void download_nullFileUrl_returns400() {
        assertThatThrownBy(() -> service.download(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("无效 URL 格式（非 doc-insight:// 也非 http(s)://）返回 400")
    void download_invalidUrlFormat_returns400() {
        String fileUrl = "ftp://example.com/file.pdf";
        assertThatThrownBy(() -> service.download(fileUrl))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    // ── http(s):// 外部代理下载 ────────────────────────────────────────────────

    @Test
    @DisplayName("https:// URL 不可达时返回 404 或 502")
    void download_externalUrlUnreachable_returns404Or502() {
        // 使用一个保证不可达的端口
        String fileUrl = "https://localhost:12345/nonexistent/file.pdf";
        assertThatThrownBy(() -> service.download(fileUrl))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    int code = ((ResponseStatusException) ex).getStatusCode().value();
                    assertThat(code).isIn(404, 502);
                });
    }
}
