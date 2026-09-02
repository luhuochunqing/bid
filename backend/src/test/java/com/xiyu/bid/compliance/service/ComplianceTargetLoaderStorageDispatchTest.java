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
import static org.mockito.ArgumentMatchers.eq;
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

    private void stubBidDocuments(final ProjectDocument... docs) {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID", null, null)).thenReturn(List.of(docs));
    }

    private ProjectDocument bidDoc(final Long id, final String name, final String fileUrl) {
        ProjectDocument doc = new ProjectDocument();
        doc.setId(id);
        doc.setProjectId(PROJECT_ID);
        doc.setName(name);
        doc.setDocumentCategory("BID");
        doc.setFileUrl(fileUrl);
        return doc;
    }

    private void stubFileStorage(final String fileUrl, final byte[] bytes) {
        when(fileStorage.load(fileUrl))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(fileUrl, null, "application/pdf", bytes)));
    }

    private void stubBidDocument(final String fileUrl) {
        stubBidDocuments(bidDoc(9L, "投标文件.pdf", fileUrl));
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
    @DisplayName("无投标文件时返回空文档（trigger 前置校验负责 4xx），且不得兜底取其他类别文档")
    void loadBidDocument_noBidDocument_returnsEmpty() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "BID", null, null)).thenReturn(List.of());

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEmpty();
        assertThat(result.pages()).isEmpty();
        verify(projectDocumentRepository, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("多册投标文件：逐份解析后合并为单个 DocumentContent（页码重编号、文件名连接）")
    void loadBidDocument_multipleBidDocuments_merged() {
        stubBidDocuments(
                bidDoc(1L, "1-价格册.pdf", "bid-agent://tender-documents/p100/price.pdf"),
                bidDoc(2L, "2-商务册.pdf", "bid-agent://tender-documents/p100/biz.pdf"));
        stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "price-bytes".getBytes(StandardCharsets.UTF_8));
        stubFileStorage("bid-agent://tender-documents/p100/biz.pdf", "biz-bytes".getBytes(StandardCharsets.UTF_8));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("1-价格册.pdf")))
                .thenReturn(DocumentContent.fromText("1-价格册.pdf", "hash-price",
                        "价格第一页\f价格第二页"));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenReturn(DocumentContent.fromText("2-商务册.pdf", "hash-biz", "商务第一页"));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).contains("价格第一页", "商务第一页");
        assertThat(result.pages()).hasSize(3);
        assertThat(result.pages()).extracting(DocumentContent.PageContent::pageNumber)
                .containsExactly(1, 2, 3);
        assertThat(result.fileName()).isEqualTo("1-价格册.pdf、2-商务册.pdf");
        assertThat(result.fileHash()).hasSize(64);
        verify(sidecarAdapter).convertDocument(any(Path.class), eq("1-价格册.pdf"));
        verify(sidecarAdapter).convertDocument(any(Path.class), eq("2-商务册.pdf"));
    }

    @Test
    @DisplayName("多册合并按上传顺序（id 升序）执行，与仓库返回的创建时间倒序无关")
    void loadBidDocument_multipleBidDocuments_sortedByIdAsc() {
        stubBidDocuments(
                bidDoc(2L, "2-商务册.pdf", "bid-agent://tender-documents/p100/biz.pdf"),
                bidDoc(1L, "1-价格册.pdf", "bid-agent://tender-documents/p100/price.pdf"));
        stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "p".getBytes(StandardCharsets.UTF_8));
        stubFileStorage("bid-agent://tender-documents/p100/biz.pdf", "b".getBytes(StandardCharsets.UTF_8));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("1-价格册.pdf")))
                .thenReturn(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenReturn(DocumentContent.fromText("2-商务册.pdf", "hash-biz", "商务正文"));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fileName()).isEqualTo("1-价格册.pdf、2-商务册.pdf");
        assertThat(result.fullText()).startsWith("价格正文");
    }

    @Test
    @DisplayName("任一册解析为空册（Sidecar 静默降级的真实失败形态）则整体失败，错误信息指名该册")
    void loadBidDocument_anyVolumeParsesEmpty_failsWholeLoad() {
        stubBidDocuments(
                bidDoc(1L, "1-价格册.pdf", "bid-agent://tender-documents/p100/price.pdf"),
                bidDoc(2L, "2-商务册.pdf", "bid-agent://tender-documents/p100/biz.pdf"));
        stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "p".getBytes(StandardCharsets.UTF_8));
        stubFileStorage("bid-agent://tender-documents/p100/biz.pdf", "b".getBytes(StandardCharsets.UTF_8));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("1-价格册.pdf")))
                .thenReturn(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"));
        // SidecarStreamDocumentAdapter 失败时不抛异常，而是返回空文本+空页的 DocumentContent
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenReturn(new DocumentContent("2-商务册.pdf", "hash-biz", "", List.of()));

        assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2-商务册.pdf");
    }

    @Test
    @DisplayName("适配器意外抛异常时整体失败并包裹该册文件名（防御路径）")
    void loadBidDocument_anyVolumeParseThrows_wrappedWithFileName() {
        stubBidDocuments(
                bidDoc(1L, "1-价格册.pdf", "bid-agent://tender-documents/p100/price.pdf"),
                bidDoc(2L, "2-商务册.pdf", "bid-agent://tender-documents/p100/biz.pdf"));
        stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "p".getBytes(StandardCharsets.UTF_8));
        stubFileStorage("bid-agent://tender-documents/p100/biz.pdf", "b".getBytes(StandardCharsets.UTF_8));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("1-价格册.pdf")))
                .thenReturn(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenThrow(new IllegalStateException("sidecar boom"));

        assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2-商务册.pdf");
    }

    @Test
    @DisplayName("全部投标文件记录 fileUrl 均为空时返回空文档（trigger 侧 422）")
    void loadBidDocument_allRecordsBlankFileUrl_returnsEmpty() {
        stubBidDocuments(
                bidDoc(1L, "0-脏记录A.pdf", ""),
                bidDoc(2L, "0-脏记录B.pdf", "   "));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEmpty();
        assertThat(result.pages()).isEmpty();
        verify(sidecarAdapter, never()).convertDocument(any(Path.class), anyString());
    }

    @Test
    @DisplayName("fileUrl 为空的脏记录被过滤，不参与核查也不阻断其余册")
    void loadBidDocument_blankFileUrlRecords_filteredOut() {
        stubBidDocuments(
                bidDoc(1L, "0-脏记录.pdf", ""),
                bidDoc(2L, "1-价格册.pdf", "bid-agent://tender-documents/p100/price.pdf"));
        stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "p".getBytes(StandardCharsets.UTF_8));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("1-价格册.pdf")))
                .thenReturn(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fileName()).isEqualTo("1-价格册.pdf");
        assertThat(result.fullText()).isEqualTo("价格正文");
        verify(sidecarAdapter, never()).convertDocument(any(Path.class), eq("0-脏记录.pdf"));
    }
}
