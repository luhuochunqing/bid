// Input: ComplianceTargetLoader 依赖 mock（三种存储前缀 fileUrl）
// Output: 验证 loadBidDocument 经 BidDocumentStorageResolver 按 ProjectDocumentStorageType 分派取流，禁止降级
// Pos: compliance/service/ - 投标文件真实取流分派单测（spec 049 US1 / T017）
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.SidecarParseOutcome;
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
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.lenient;
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
    private SidecarComplianceDocumentAdapter sidecarAdapter;
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
        // 视觉链路默认直通：页面图像抽取以入参文档原样返回，避免未打桩的 mock 返回 null 吞掉解析结果
        lenient().when(sidecarAdapter.withPageImages(any(Path.class), anyString(), any(DocumentContent.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
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
        when(sidecarAdapter.convertDocument(any(Path.class), anyString()))
                .thenReturn(SidecarParseOutcome.ok(parsed, List.of()));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fullText()).isEqualTo("真实投标正文");
        verify(fileStorage).load("bid-agent://tender-documents/p100/bid.pdf");
        verify(obsShareUrlSigner, never()).trySign(anyString());
    }

    @Test
    @DisplayName("文本解析完成后且临时文件删除前抽取页面图像并保留到质检文档")
    void loadBidDocument_extractsPageImagesBeforeTemporaryFileDeletion() throws Exception {
        stubBidDocument("bid-agent://tender-documents/p100/bid.pdf");
        stubFileStorage("bid-agent://tender-documents/p100/bid.pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));
        DocumentContent parsed = DocumentContent.fromText("投标文件.pdf", "hash-image", "投标函正文");
        when(sidecarAdapter.convertDocument(any(Path.class), anyString()))
                .thenReturn(SidecarParseOutcome.ok(parsed, List.of()));
        PageImage image = new PageImage(0, "image/png", "投标函", 1, 100, 100, "aGk=");
        when(sidecarAdapter.withPageImages(any(Path.class), anyString(), eq(parsed))).thenAnswer(invocation -> {
            assertThat(Files.exists(invocation.getArgument(0, Path.class))).isTrue();
            return new DocumentContent(parsed.fileName(), parsed.fileHash(), parsed.fullText(),
                    parsed.pages(), List.of(image));
        });

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.images()).containsExactly(image);
        verify(sidecarAdapter).withPageImages(any(Path.class), eq("投标文件.pdf"), eq(parsed));
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
        when(sidecarAdapter.convertDocument(any(Path.class), anyString()))
                .thenReturn(SidecarParseOutcome.ok(parsed, List.of()));

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
        when(sidecarAdapter.convertDocument(any(Path.class), anyString()))
                .thenReturn(SidecarParseOutcome.ok(parsed, List.of()));

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
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("1-价格册.pdf", "hash-price",
                        "价格第一页\f价格第二页"), List.of()));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("2-商务册.pdf", "hash-biz", "商务第一页"), List.of()));

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
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"), List.of()));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("2-商务册.pdf", "hash-biz", "商务正文"), List.of()));

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
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"), List.of()));
        // SidecarStreamDocumentAdapter 失败时不抛异常，而是返回非 OK 分类（spec 066：空册 = EMPTY_TEXT）
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenReturn(SidecarParseOutcome.emptyText("warnings=[low_text_density]", List.of("low_text_density")));

        assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2-商务册.pdf")
                .hasMessageContaining("无文本层");
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
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"), List.of()));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("2-商务册.pdf")))
                .thenThrow(new IllegalStateException("sidecar boom"));

        assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2-商务册.pdf");
    }

    @Test
    @DisplayName("null 文件名的册走 displayFileName 单点兜底为 bid_document.pdf（跨写点契约锚定）")
    void loadBidDocument_nullName_fallsBackToCanonicalName() {
        stubBidDocuments(bidDoc(1L, null, "bid-agent://tender-documents/p100/price.pdf"));
        stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "p".getBytes(StandardCharsets.UTF_8));
        when(sidecarAdapter.convertDocument(any(Path.class), eq("bid_document.pdf")))
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("bid_document.pdf", "hash-price", "价格正文"), List.of()));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fileName()).isEqualTo("bid_document.pdf");
        verify(sidecarAdapter).convertDocument(any(Path.class), eq("bid_document.pdf"));
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
                .thenReturn(SidecarParseOutcome.ok(DocumentContent.fromText("1-价格册.pdf", "hash-price", "价格正文"), List.of()));

        DocumentContent result = loader.loadBidDocument(PROJECT_ID);

        assertThat(result.fileName()).isEqualTo("1-价格册.pdf");
        assertThat(result.fullText()).isEqualTo("价格正文");
        verify(sidecarAdapter, never()).convertDocument(any(Path.class), eq("0-脏记录.pdf"));
    }

    @Nested
    @DisplayName("解析失败分类文案（spec 066 契约）")
    class ParseFailureMessageTests {

        private void stubSingleBidWithOutcome(final SidecarParseOutcome outcome) {
            stubBidDocument("bid-agent://tender-documents/p100/bid.pdf");
            stubFileStorage("bid-agent://tender-documents/p100/bid.pdf", "b".getBytes(StandardCharsets.UTF_8));
            when(sidecarAdapter.convertDocument(any(Path.class), anyString())).thenReturn(outcome);
        }

        @Test
        @DisplayName("TIMEOUT：报解析超时且不引导重传")
        void timeoutMessage() {
            stubSingleBidWithOutcome(SidecarParseOutcome.timeout("解析未在等待上限（645s）内完成"));

            assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("解析超时")
                    .hasMessageContaining("投标文件.pdf")
                    .hasMessageContaining("稍后重试")
                    .hasMessageNotContaining("请确认文件可正常打开")
                    .hasMessageNotContaining("请重新上传");
        }

        @Test
        @DisplayName("SERVICE_ERROR：报服务异常且不引导重传")
        void serviceErrorMessage() {
            stubSingleBidWithOutcome(SidecarParseOutcome.serviceError("连接解析服务失败：Connection refused"));

            assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("服务异常")
                    .hasMessageContaining("稍后重试")
                    .hasMessageNotContaining("请确认文件可正常打开")
                    .hasMessageNotContaining("请重新上传");
        }

        @Test
        @DisplayName("SERVICE_ERROR（413 超上限）：不承诺稍后重试")
        void oversizeMessage() {
            stubSingleBidWithOutcome(SidecarParseOutcome.oversize("文件超过解析服务大小上限（413）"));

            assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超过解析服务大小上限")
                    .hasMessageNotContaining("稍后重试")
                    .hasMessageNotContaining("请重新上传");
        }

        @Test
        @DisplayName("EMPTY_TEXT：报图片扫描件/无文本层且不引导重传")
        void emptyTextMessage() {
            stubSingleBidWithOutcome(SidecarParseOutcome.emptyText("warnings=[low_text_density]", List.of("low_text_density")));

            assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("图片扫描件")
                    .hasMessageContaining("无文本层")
                    .hasMessageNotContaining("请确认文件可正常打开")
                    .hasMessageNotContaining("请重新上传");
        }

        @Test
        @DisplayName("UNPARSEABLE：唯一允许引导重传的解析分类")
        void unparseableMessage() {
            stubSingleBidWithOutcome(SidecarParseOutcome.unparseable("解析服务返回 422"));

            assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("请确认文件可正常打开后重新上传");
        }

        @Test
        @DisplayName("多册：失败文案指名失败册")
        void multiVolumeNamesFailingVolume() {
            stubBidDocuments(
                    bidDoc(1L, "1-价格册.pdf", "bid-agent://tender-documents/p100/price.pdf"),
                    bidDoc(2L, "2-技术册.pdf", "bid-agent://tender-documents/p100/tech.pdf"));
            stubFileStorage("bid-agent://tender-documents/p100/price.pdf", "p".getBytes(StandardCharsets.UTF_8));
            stubFileStorage("bid-agent://tender-documents/p100/tech.pdf", "t".getBytes(StandardCharsets.UTF_8));
            when(sidecarAdapter.convertDocument(any(Path.class), eq("1-价格册.pdf")))
                    .thenReturn(SidecarParseOutcome.ok(
                            DocumentContent.fromText("1-价格册.pdf", "h1", "价格正文"), List.of()));
            when(sidecarAdapter.convertDocument(any(Path.class), eq("2-技术册.pdf")))
                    .thenReturn(SidecarParseOutcome.timeout("解析未在等待上限（645s）内完成"));

            assertThatThrownBy(() -> loader.loadBidDocument(PROJECT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("2-技术册.pdf")
                    .hasMessageContaining("解析超时");
        }
    }
}
