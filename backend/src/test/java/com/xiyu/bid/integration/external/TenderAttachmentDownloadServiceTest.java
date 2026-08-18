package com.xiyu.bid.integration.external;

import com.xiyu.bid.file.application.ObsShareUrlSigner;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TenderAttachmentDownloadService 单元测试（CO-280 403 修复）。
 * 覆盖 doc-insight:// 本地下载 + http(s):// 外部代理下载 + 边界校验。
 */
@DisplayName("TenderAttachmentDownloadService – CRM 集成附件下载")
class TenderAttachmentDownloadServiceTest {

    private TenderAttachmentDownloadService service;
    private ObsShareUrlSigner obsShareUrlSigner;

    @TempDir
    Path tempUploadDir;

    @BeforeEach
    void setUp() {
        obsShareUrlSigner = org.mockito.Mockito.mock(ObsShareUrlSigner.class);
        service = new TenderAttachmentDownloadService(obsShareUrlSigner);
        ReflectionTestUtils.setField(service, "configuredUploadDir", tempUploadDir.toString());
    }

    // ── obs-direct: OBS 直传文件（302 重定向实时签发）─────────────────

    @Test
    @DisplayName("obs-direct:{uploadId} 签发成功时返回 302 重定向到预签名 URL")
    void download_obsDirectSigned_returns302() {
        org.mockito.Mockito.when(obsShareUrlSigner.trySign("obs-direct:upload-abc"))
                .thenReturn(Optional.of("https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/bids/test.png?Signature=xxx"));

        ResponseEntity<Resource> response = service.download("obs-direct:upload-abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString())
                .startsWith("https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/");
    }

    @Test
    @DisplayName("obs-direct:{uploadId} 签发失败（文件不存在/OBS 故障）返回 404")
    void download_obsDirectSignFailed_returns404() {
        org.mockito.Mockito.when(obsShareUrlSigner.trySign("obs-direct:non-existent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download("obs-direct:non-existent"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(404));
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
        // 使用一个保证不可达的端口（非私有地址）
        String fileUrl = "https://example.com:12345/nonexistent/file.pdf";
        assertThatThrownBy(() -> service.download(fileUrl))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    int code = ((ResponseStatusException) ex).getStatusCode().value();
                    assertThat(code).isIn(404, 502);
                });
    }

    // ── SSRF 防护 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SSRF: localhost 被拒绝返回 400")
    void download_localhost_returns400() {
        assertThatThrownBy(() -> service.download("https://localhost/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 127.0.0.1 被拒绝返回 400")
    void download_loopbackIp_returns400() {
        assertThatThrownBy(() -> service.download("https://127.0.0.1/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 10.x 私有 IP 被拒绝返回 400")
    void download_privateIp10_returns400() {
        assertThatThrownBy(() -> service.download("https://10.0.0.1/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 192.168.x 私有 IP 被拒绝返回 400")
    void download_privateIp192_returns400() {
        assertThatThrownBy(() -> service.download("https://192.168.1.1/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 172.16.x 私有 IP 被拒绝返回 400")
    void download_privateIp172_returns400() {
        assertThatThrownBy(() -> service.download("https://172.16.0.1/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 0.0.0.0 被拒绝返回 400")
    void download_anyLocalIp_returns400() {
        assertThatThrownBy(() -> service.download("https://0.0.0.0/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 配置域名白名单后，非白名单域名被拒绝返回 400")
    void download_nonWhitelistedHost_returns400() {
        ReflectionTestUtils.setField(service, "allowedExternalHosts", "allowed.example.com");
        assertThatThrownBy(() -> service.download("https://evil.com/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    @Test
    @DisplayName("SSRF: 配置域名白名单后，白名单域名通过校验")
    void download_whitelistedHost_passesValidation() {
        ReflectionTestUtils.setField(service, "allowedExternalHosts", "allowed.example.com");
        // 白名单校验通过后，由于实际不可达会返回 404/502 而非 400
        assertThatThrownBy(() -> service.download("https://allowed.example.com:12345/file.pdf"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    int code = ((ResponseStatusException) ex).getStatusCode().value();
                    assertThat(code).isIn(404, 502);
                });
    }

    @Test
    @DisplayName("SSRF: isPrivateOrLoopbackAddress 正确识别私有地址")
    void isPrivateOrLoopbackAddress_correctlyIdentifiesPrivateAddresses() {
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("localhost")).isTrue();
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("127.0.0.1")).isTrue();
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("10.0.0.1")).isTrue();
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("192.168.1.1")).isTrue();
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("172.16.0.1")).isTrue();
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("0.0.0.0")).isTrue();
        assertThat(TenderAttachmentDownloadService.isPrivateOrLoopbackAddress("example.com")).isFalse();
    }
}
