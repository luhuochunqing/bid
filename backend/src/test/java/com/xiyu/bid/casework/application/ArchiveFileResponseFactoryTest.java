package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * spec 039：档案文件响应工厂测试。
 * obs-direct: 伪协议 → 302 重定向预签名 URL；本地物理路径 → 流式 200；越界 → 400 语义异常。
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
        when(obsShareUrlSigner.trySign("obs-direct:upload-123"))
                .thenReturn(Optional.of("https://obs.example.com/signed/abc?signature=x"));

        ResponseEntity<Resource> response = factory.build(file, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("https://obs.example.com/signed/abc?signature=x");
    }

    @Test
    void build_obsDirectPath_signFailed_shouldThrowNotFound() {
        ArchiveFile file = archiveFile("obs-direct:missing-upload");
        when(obsShareUrlSigner.trySign("obs-direct:missing-upload")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.build(file, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void build_obsDirectPath_signReturnedRawValue_shouldThrowNotFound() {
        // 防御：trySign 对非 obs-direct 输入原样返回；此处模拟异常返回原值时不得 302 到伪协议地址
        ArchiveFile file = archiveFile("obs-direct:raw-value");
        when(obsShareUrlSigner.trySign("obs-direct:raw-value"))
                .thenReturn(Optional.of("obs-direct:raw-value"));

        assertThatThrownBy(() -> factory.build(file, true))
                .isInstanceOf(ResourceNotFoundException.class);
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
