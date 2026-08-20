// Input: ComplianceTargetLoader 依赖 mock（三种存储前缀 fileUrl）
// Output: 验证 loadBidDocument 经 BidDocumentStorageResolver 按 ProjectDocumentStorageType 分派取流，禁止降级
// Pos: compliance/service/ - 投标文件真实取流分派单测（spec 049 US1 / T017）
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.core.ProjectDocumentStorageType;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * spec 049 US1：投标文件必须从生产存储真实取流（三种前缀分派），禁止元数据降级.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceTargetLoaderStorageDispatchTest {

    private static final Long PROJECT_ID = 100L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private SidecarStreamDocumentAdapter sidecarAdapter;
    @Mock
    private ProjectDocumentFileStorage fileStorage;
    @Mock
    private ObsShareUrlSigner obsShareUrlSigner;

    @TempDir
    Path tempDir;

    private ComplianceTargetLoader loader;

    /** 可覆写 OBS 下载步骤的测试解析器（避免单测走真实 HTTP）. */
    private class TestableResolver extends BidDocumentStorageResolver {
        TestableResolver() {
            super(fileStorage, obsShareUrlSigner);
        }

        @Override
        protected Path downloadToTempFile(final String url, final String fileName) {
            assertThat(url).isEqualTo("https://obs.example.com/presigned/bid.pdf");
            try {
                Path file = tempDir.resolve("obs-downloaded.pdf");
                Files.writeString(file, "obs-downloaded-pdf-bytes", StandardCharsets.UTF_8);
                return file;
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @BeforeEach
    void setUp() {
        loader = new ComplianceTargetLoader(projectRepository, tenderRepository,
                projectDocumentRepository, sidecarAdapter, new TestableResolver());
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("测试项目");
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private void stubBidDocument(final String fileUrl) {
        ProjectDocument doc = new ProjectDocument();
        doc.setId(9L);
        doc.setProjectId(PROJECT_ID);
        doc.setName("投标文件.pdf");
        doc.setDocumentCategory("BID");
        doc.setFileUrl(fileUrl);
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID", null, null)).thenReturn(List.of(doc));
    }

    @Test
    @DisplayName("bid-agent:// 前缀走 ProjectDocumentFileStorage 流式读取并交 Sidecar 解析")
    void loadBidDocument_bidAgentPrefix_dispatchesToFileStorage() {
        stubBidDocument("bid-agent://tender-documents/p100/bid.pdf");
        byte[] pdfBytes = "bid-agent-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(fileStorage.load("bid-agent://tender-documents/p100/bid.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "bid-agent://tender-documents/p100/bid.pdf", null, "application/pdf", pdfBytes)));
        DocumentContent parsed = DocumentContent.fromText("投标文件.pdf", "hash-a", "真实投标正文");
        when(sidecarAdapter.convertDocument(any(Path.class), anyString())).thenReturn(parsed);

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEqualTo("真实投标正文");
        verify(fileStorage).load("bid-agent://tender-documents/p100/bid.pdf");
        verify(obsShareUrlSigner, never()).trySign(anyString());
    }

    @Test
    @DisplayName("doc-insight:// 前缀走 ProjectDocumentFileStorage 流式读取并交 Sidecar 解析")
    void loadBidDocument_docInsightPrefix_dispatchesToFileStorage() {
        stubBidDocument("doc-insight://docs/p100/bid.pdf");
        byte[] pdfBytes = "doc-insight-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(fileStorage.load("doc-insight://docs/p100/bid.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://docs/p100/bid.pdf", null, "application/pdf", pdfBytes)));
        DocumentContent parsed = DocumentContent.fromText("投标文件.pdf", "hash-b", "真实投标正文B");
        when(sidecarAdapter.convertDocument(any(Path.class), anyString())).thenReturn(parsed);

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEqualTo("真实投标正文B");
        verify(fileStorage).load("doc-insight://docs/p100/bid.pdf");
        verify(obsShareUrlSigner, never()).trySign(anyString());
    }

    @Test
    @DisplayName("obs-direct: 前缀走 ObsShareUrlSigner 预签名下载后交 Sidecar 解析")
    void loadBidDocument_obsDirectPrefix_dispatchesToPresignedDownload() {
        stubBidDocument("obs-direct:upload-123");
        when(obsShareUrlSigner.trySign("obs-direct:upload-123"))
                .thenReturn(Optional.of("https://obs.example.com/presigned/bid.pdf"));
        DocumentContent parsed = DocumentContent.fromText("投标文件.pdf", "hash-c", "真实投标正文C");
        when(sidecarAdapter.convertDocument(any(Path.class), anyString())).thenReturn(parsed);

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEqualTo("真实投标正文C");
        verify(obsShareUrlSigner).trySign("obs-direct:upload-123");
        verify(fileStorage, never()).load(anyString());
    }

    @Test
    @DisplayName("未知存储前缀抛业务异常，禁止降级为元数据文本")
    void loadBidDocument_unknownPrefix_throwsInsteadOfFallback() {
        stubBidDocument("http://legacy.local/bid.pdf");

        assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投标文件");

        verify(sidecarAdapter, never()).convertDocument(any(Path.class), anyString());
        assertThat(ProjectDocumentStorageType.fromFileUrl("http://legacy.local/bid.pdf"))
                .isEqualTo(ProjectDocumentStorageType.UNKNOWN);
    }

    @Test
    @DisplayName("本地存储读不到文件时抛业务异常，禁止降级")
    void loadBidDocument_fileStorageEmpty_throwsInsteadOfFallback() {
        stubBidDocument("bid-agent://tender-documents/p100/missing.pdf");
        when(fileStorage.load("bid-agent://tender-documents/p100/missing.pdf"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                .isInstanceOf(BusinessException.class);

        verify(sidecarAdapter, never()).convertDocument(any(Path.class), anyString());
    }

    @Test
    @DisplayName("无投标文件时返回空文档（trigger 前置校验负责 4xx）")
    void loadBidDocument_noBidDocument_returnsEmpty() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID", null, null)).thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID))
                .thenReturn(List.of());

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEmpty();
        assertThat(result.pages()).isEmpty();
    }
}
