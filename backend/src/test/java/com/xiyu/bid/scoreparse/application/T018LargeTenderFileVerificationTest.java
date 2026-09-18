// Input: 80MB 扫描版 PDF 与 <50MB 压缩版文件流
// Output: quickstart.md V1~V4 验证：评分项一致性比对 / 30MB 边界 / Evidence Pipeline / 资源定点释放 / 零 OOM
// Pos: scoreparse/application — spec 065 T018 验收测试
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Spec 065 T018: 跑通 quickstart.md V1~V4（含 80MB/压缩版一致性比对与生命周期验证）。
 */
class T018LargeTenderFileVerificationTest {

    private static final long PROJECT_ID = 65001L;
    private static final long SIZE_80MB = 80L * 1024 * 1024;
    private static final long SIZE_15MB = 15L * 1024 * 1024;
    private static final long RAM_BUFFER_30MB = 30L * 1024 * 1024; // 31457280L

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String baseUrl;
    private TempBidFileStore tempFileStore;
    private StreamingBidFileFetcher streamingFetcher;
    private SidecarStreamTextExtractor streamTextExtractor;
    private InitiationTenderTextResolver resolver;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private BidTenderDocumentSnapshotRepository snapshotRepository;
    @Mock
    private ProjectDocumentFileStorage fileStorage;
    @Mock
    private TenderDocumentStorage tenderDocumentStorage;
    @Mock
    private TenderDocumentTextExtractor textExtractor;
    @Mock
    private ObsShareUrlSigner obsShareUrlSigner;

    private AutoCloseable mocksCloseable;

    @BeforeEach
    void setUp() throws IOException {
        mocksCloseable = MockitoAnnotations.openMocks(this);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        tempFileStore = new TempBidFileStore(tempDir);
        streamingFetcher = new StreamingBidFileFetcher(tempFileStore, RAM_BUFFER_30MB, 536870912L, 30);
        streamTextExtractor = new SidecarStreamTextExtractor(baseUrl, "", 10, 30);

        resolver = new InitiationTenderTextResolver(
                projectDocumentRepository, snapshotRepository, fileStorage,
                tenderDocumentStorage, textExtractor, obsShareUrlSigner,
                streamingFetcher, streamTextExtractor, tempFileStore,
                RAM_BUFFER_30MB, 5000000L, 536870912L);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop(0);
        if (mocksCloseable != null) {
            mocksCloseable.close();
        }
    }

    private void serveChunkedStream(String path, long totalSize) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, totalSize);
            try (OutputStream out = exchange.getResponseBody()) {
                byte[] chunk = new byte[64 * 1024];
                long written = 0;
                while (written < totalSize) {
                    int toWrite = (int) Math.min(chunk.length, totalSize - written);
                    out.write(chunk, 0, toWrite);
                    written += toWrite;
                }
            }
            exchange.close();
        });
    }

    private void serveConvertStreamText(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        server.createContext("/convert-stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
    }

    private DocumentArtifact buildStandardScoreArtifact() {
        List<List<String>> rows = List.of(
                List.of("编号", "评分项", "评分标准", "分值"),
                List.of("1", "商务能力", "资质齐全得满分", "20"),
                List.of("2", "技术方案", "方案完整可行得满分", "50"),
                List.of("3", "售后服务", "响应及时得满分", "30")
        );
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < rows.get(row).size(); col++) {
                String text = rows.get(row).get(col);
                String cellId = "p1-t0000-r" + row + "-c" + col;
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                        cellId, row, col, text, text, 1, 1, false,
                        "pdf:page=1;table=0;row=" + row + ";col=" + col));
            }
        }
        return new DocumentArtifact(
                new DocumentArtifact.ArtifactMeta("sha256:standard", "pdf", "PDF", "pdf-table-v1"),
                List.of(new DocumentArtifact.CanonicalTable(
                        "p1-t0000", 1, "", rows.size(), 4, List.of(), cells, true)),
                List.of()
        );
    }

    /**
     * V1. 大文件解析一次通过（US1）与 50MB 以内压缩版一致性比对：
     * 1. 80MB 原文件：超过 30MB 阈值，spill 落盘为 OnDisk，流式提取与 Evidence Pipeline 正常执行；
     * 2. 15MB 压缩版：<= 30MB 走 InMemory 内存快路径；
     * 3. 结果断言：评分项数量一致（3项）、总权重一致（100分）、各项权重完全相同；
     * 4. 生命周期断言：任务完成后 80MB 临时文件必须定点删除，配额完全归还，无 OOM。
     */
    @Test
    void v1_80MbFileVsCompressed_consistencyAndLifecycleCheck() throws Exception {
        serveChunkedStream("/obs/tender-80mb.pdf", SIZE_80MB);
        serveChunkedStream("/obs/tender-compressed.pdf", SIZE_15MB);
        serveConvertStreamText("### 评分办法\n商务能力20分，技术方案50分，售后服务30分，满分100分。");

        AtomicBoolean onDiskExtractorCalled = new AtomicBoolean(false);
        AtomicBoolean inMemoryExtractorCalled = new AtomicBoolean(false);

        SidecarDocumentArtifactExtractor artifactExtractor =
                new SidecarDocumentArtifactExtractor("http://localhost:9", "", 1, new ObjectMapper()) {
                    @Override
                    public DocumentArtifact extract(Path file, String fileName) {
                        onDiskExtractorCalled.set(true);
                        assertThat(Files.exists(file)).isTrue();
                        return buildStandardScoreArtifact();
                    }

                    @Override
                    public DocumentArtifact extract(byte[] content, String fileName) {
                        inMemoryExtractorCalled.set(true);
                        assertThat(content.length).isEqualTo(SIZE_15MB);
                        return buildStandardScoreArtifact();
                    }
                };

        // --- 1. 执行 80MB 原文件解析流 ---
        ProjectDocument doc80Mb = ProjectDocument.builder()
                .id(801L).name("80MB扫描版招标文件.pdf").fileUrl("obs-direct:tender-80mb").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(doc80Mb));
        when(obsShareUrlSigner.trySign("obs-direct:tender-80mb"))
                .thenReturn(Optional.of(baseUrl + "/obs/tender-80mb.pdf"));

        TenderExecutionSource exec80 = resolver.resolveExecutionSource(PROJECT_ID);
        assertThat(exec80.source()).isNotNull();
        assertThat(exec80.loaded()).isInstanceOf(LoadedBidFile.OnDisk.class);
        LoadedBidFile.OnDisk onDisk80 = (LoadedBidFile.OnDisk) exec80.loaded();
        Path tempFile80 = onDisk80.tempFile();

        // 断言落盘文件存在且大小恰好 80MB
        assertThat(Files.exists(tempFile80)).isTrue();
        assertThat(Files.size(tempFile80)).isEqualTo(SIZE_80MB);
        assertThat(tempFileStore.getCurrentTotalBytes()).isEqualTo(SIZE_80MB);

        // 进入 Evidence Pipeline 处理
        StructuredTableResult result80;
        try {
            result80 = StructuredTableExtractionHandler.extract(
                    exec80.source(), exec80.loaded(), artifactExtractor, null);
        } finally {
            // 模拟 Worker 最外层 finally 释放
            resolver.releaseTenderFile(exec80.loaded());
        }

        assertThat(onDiskExtractorCalled.get()).isTrue();
        assertThat(result80.evidencePipeline()).isTrue();
        assertThat(result80.candidates()).hasSize(3);

        // 验证 80MB 临时文件与配额定点释放
        assertThat(Files.exists(tempFile80)).isFalse();
        assertThat(tempFileStore.getCurrentTotalBytes()).isZero();

        // --- 2. 执行 15MB 压缩版解析流 ---
        ProjectDocument docComp = ProjectDocument.builder()
                .id(802L).name("15MB压缩版招标文件.pdf").fileUrl("obs-direct:tender-compressed").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(docComp));
        when(obsShareUrlSigner.trySign("obs-direct:tender-compressed"))
                .thenReturn(Optional.of(baseUrl + "/obs/tender-compressed.pdf"));
        when(textExtractor.extract(eq("15MB压缩版招标文件.pdf"), any(), any()))
                .thenReturn(new ExtractedTenderDocument(
                        "15MB压缩版招标文件.pdf", "pdf",
                        "### 评分办法\n商务能力20分，技术方案50分，售后服务30分，满分100分。", 30, "md"));

        TenderExecutionSource execComp = resolver.resolveExecutionSource(PROJECT_ID);
        assertThat(execComp.source()).isNotNull();
        assertThat(execComp.loaded()).isInstanceOf(LoadedBidFile.InMemory.class);

        StructuredTableResult resultComp;
        try {
            resultComp = StructuredTableExtractionHandler.extract(
                    execComp.source(), execComp.loaded(), artifactExtractor, null);
        } finally {
            resolver.releaseTenderFile(execComp.loaded());
        }

        assertThat(inMemoryExtractorCalled.get()).isTrue();
        assertThat(resultComp.evidencePipeline()).isTrue();
        assertThat(resultComp.candidates()).hasSize(3);

        // --- 3. 严格比对：80MB 与压缩版结果完全一致 ---
        assertThat(result80.candidates())
                .extracting(ScoreCandidate::dim)
                .containsExactlyElementsOf(resultComp.candidates().stream().map(ScoreCandidate::dim).toList());

        assertThat(result80.candidates())
                .extracting(ScoreCandidate::weight)
                .containsExactlyElementsOf(resultComp.candidates().stream().map(ScoreCandidate::weight).toList());

        BigDecimal sum80 = result80.candidates().stream()
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumComp = resultComp.candidates().stream()
                .map(ScoreCandidate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum80).isEqualByComparingTo("100.00");
        assertThat(sumComp).isEqualByComparingTo("100.00");
        assertThat(sum80).isEqualByComparingTo(sumComp);
    }

    /**
     * V2. 文本量超限明确拒绝（US2）：
     * 当提取文本量超过上限（约 500 万字）时，明确标记 FAILED，给出含有动态上限的提示文案。
     */
    @Test
    void v2_oversizedTextExceedingLimit_failsWithDynamicMessage() {
        ProjectDocument document = ProjectDocument.builder()
                .id(803L).name("超大文本.pdf").fileUrl("doc-insight://t/huge.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/huge.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/huge.pdf", "/tmp/huge.pdf", "application/pdf", "short".getBytes())));
        when(textExtractor.extract(eq("超大文本.pdf"), eq("pdf"), any()))
                .thenReturn(new ExtractedTenderDocument("超大文本.pdf", "pdf", "X".repeat(5000001), 5000001, "md"));

        TenderExecutionSource exec = resolver.resolveExecutionSource(PROJECT_ID);

        assertThat(exec.source()).isNull();
        assertThat(exec.emptyReason()).contains("招标文件文本量超过上限（约 500 万字），无法解析，请检查是否误传或拆分文档");
    }

    /**
     * V4.1 边界回归：刚好 30MB 文件走内存快路径（<= 30MB 走 InMemory）。
     */
    @Test
    void v4_1_exactly30MbFile_usesMemoryFastPath() {
        byte[] exact30Mb = new byte[(int) RAM_BUFFER_30MB];
        ProjectDocument document = ProjectDocument.builder()
                .id(804L).name("刚好30MB.pdf").fileUrl("doc-insight://t/30mb.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/30mb.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/30mb.pdf", "/tmp/30mb.pdf", "application/pdf", exact30Mb)));
        when(textExtractor.extract(eq("刚好30MB.pdf"), eq("pdf"), any()))
                .thenReturn(new ExtractedTenderDocument("刚好30MB.pdf", "pdf", "商务得分100分", 8, "md"));

        TenderExecutionSource exec = resolver.resolveExecutionSource(PROJECT_ID);

        assertThat(exec.source()).isNotNull();
        assertThat(exec.loaded()).isInstanceOf(LoadedBidFile.InMemory.class);
        assertThat(tempFileStore.getCurrentTotalBytes()).isZero();
    }

    /**
     * V4.2 边界回归：超大且有可用历史底稿时，静默回退历史底稿。
     */
    @Test
    void v4_2_oversizedWithHistoricalSnapshot_fallsBackToSnapshotSilently() {
        ProjectDocument document = ProjectDocument.builder()
                .id(805L).name("超大配额文件.pdf").fileUrl("doc-insight://t/over-quota.pdf").fileType("pdf").build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of(document));
        when(fileStorage.load("doc-insight://t/over-quota.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "doc-insight://t/over-quota.pdf", "/tmp/over-quota.pdf", "application/pdf", new byte[20])));
        // 模拟配额超限（或抛出配额异常）
        InitiationTenderTextResolver quotaResolver = new InitiationTenderTextResolver(
                projectDocumentRepository, snapshotRepository, fileStorage,
                tenderDocumentStorage, textExtractor, obsShareUrlSigner,
                streamingFetcher, streamTextExtractor, tempFileStore,
                RAM_BUFFER_30MB, 5000000L, 10L); // max-file-bytes 仅 10 字节

        BidTenderDocumentSnapshot snapshot = new BidTenderDocumentSnapshot();
        snapshot.setFileName("历史底稿.pdf");
        snapshot.setFileUrl("history://snapshot.pdf");
        snapshot.setExtractedText("历史底稿有效评分文本：技术分100分");

        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.of(snapshot));

        TenderExecutionSource exec = quotaResolver.resolveExecutionSource(PROJECT_ID);

        assertThat(exec.source()).isNotNull();
        assertThat(exec.source().fileName()).isEqualTo("历史底稿.pdf");
        assertThat(exec.source().text()).isEqualTo("历史底稿有效评分文本：技术分100分");
    }

    /**
     * V4.3 边界回归：无文件且无底稿时，明确提示「请先在立项阶段上传招标文件」。
     */
    @Test
    void v4_3_noFileNoSnapshot_returnsUploadPrompt() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                PROJECT_ID, "TENDER", null, null))
                .thenReturn(List.of());
        when(snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(PROJECT_ID))
                .thenReturn(Optional.empty());

        TenderExecutionSource exec = resolver.resolveExecutionSource(PROJECT_ID);

        assertThat(exec.source()).isNull();
        assertThat(exec.emptyReason()).isEqualTo("请先在立项阶段上传招标文件");
    }
}
