package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * spec 039：档案文件响应工厂测试。
 * obs-direct: 伪协议 → 302 重定向预签名 URL（统一走 ObsDirectRedirects，404/502 语义精确）；
 * 本地物理路径 → 流式 200；越界 → 400 语义异常。
 */
class ArchiveFileResponseFactoryTest {

    private ObsShareUrlSigner obsShareUrlSigner;
    private ArchiveFileResponseFactory factory;

    @TempDir
    Path uploadDir;

    @BeforeEach
    void setUp() {
        obsShareUrlSigner = mock(ObsShareUrlSigner.class);
        factory = new ArchiveFileResponseFactory(obsShareUrlSigner);
        ReflectionTestUtils.setField(factory, "configuredUploadDir", uploadDir.toString());
    }

    private ArchiveFile archiveFile(String filePath) {
        ArchiveFile file = new ArchiveFile();
        file.setId(7001L);
        file.setArchiveId(88L);
        file.setFileName("投标文件.pdf");
        file.setDocumentCategory("BID");
        file.setFilePath(filePath);
        file.setFileSize(0L);
        file.setUploadUserId(1L);
        file.setUploadUserName("王工");
        return file;
    }

    @Test
    void build_obsDirectPath_shouldReturn302WithSignedUrl() {
        ArchiveFile file = archiveFile("obs-direct:upload-123");
        when(obsShareUrlSigner.signObsDirect("obs-direct:upload-123"))
                .thenReturn(ObsShareUrlSigner.SignResult.signed("https://obs.example.com/signed/abc?signature=x"));

        ResponseEntity<Resource> response = factory.build(file, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("https://obs.example.com/signed/abc?signature=x");
    }

    @Test
    void build_obsDirectPath_fileNotFound_shouldThrow404() {
        ArchiveFile file = archiveFile("obs-direct:missing-upload");
        when(obsShareUrlSigner.signObsDirect("obs-direct:missing-upload"))
                .thenReturn(ObsShareUrlSigner.SignResult.of(ObsShareUrlSigner.SignOutcome.FILE_NOT_FOUND));

        assertThatThrownBy(() -> factory.build(file, false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void build_obsDirectPath_signFailed_shouldThrow502() {
        // OBS 上游故障 ≠ 资源不存在，语义上为 502（可重试），避免误导排障
        ArchiveFile file = archiveFile("obs-direct:obs-down");
        when(obsShareUrlSigner.signObsDirect("obs-direct:obs-down"))
                .thenReturn(ObsShareUrlSigner.SignResult.of(ObsShareUrlSigner.SignOutcome.SIGN_FAILED));

        assertThatThrownBy(() -> factory.build(file, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void build_localPathWithinUploadDir_shouldReturn200() throws Exception {
        Path dir = uploadDir.resolve("tender-file").resolve("1001");
        Files.createDirectories(dir);
        Path realFile = dir.resolve("abc.pdf");
        Files.write(realFile, "pdf-content".getBytes());

        ResponseEntity<Resource> response = factory.build(archiveFile(realFile.toString()), false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(11L);
        assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;");
    }

    @Test
    void build_previewLocalPath_shouldInferContentType() throws Exception {
        Path realFile = uploadDir.resolve("doc.pdf");
        Files.write(realFile, "x".getBytes());

        ResponseEntity<Resource> response = factory.build(archiveFile(realFile.toString()), true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).hasToString("application/pdf");
        assertThat(response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("inline;");
    }

    @Test
    void build_pathOutsideUploadDir_shouldReject() throws Exception {
        Path outside = Files.createTempFile("outside-archive", ".pdf");

        assertThatThrownBy(() -> factory.build(archiveFile(outside.toString()), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件路径越界");

        Files.deleteIfExists(outside);
    }
}
