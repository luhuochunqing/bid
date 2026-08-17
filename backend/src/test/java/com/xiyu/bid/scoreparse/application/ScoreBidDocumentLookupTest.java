package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreBidDocumentLookupTest {

    private static final Long PROJECT_ID = 100L;

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private TenderDocumentStorage documentStorage;
    @Mock
    private TenderDocumentTextExtractor textExtractor;
    @Mock
    private ProjectDocumentFileStorage fileStorage;
    @Mock
    private ObsShareUrlSigner obsShareUrlSigner;

    private ScoreBidDocumentLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new ScoreBidDocumentLookup(
                projectDocumentRepository, documentStorage, textExtractor,
                fileStorage, obsShareUrlSigner,
                url -> "OBS文件内容".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void loadText_obsDirectFile_successfullyExtractsText() {
        String fileUrl = "obs-direct:bid/project100/sample.pdf";
        ProjectDocument doc = ProjectDocument.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .name("投标文件.pdf")
                .fileType("pdf")
                .fileUrl(fileUrl)
                .build();

        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of(doc));
        when(obsShareUrlSigner.trySign(fileUrl))
                .thenReturn(Optional.of("https://obs.huaweicloud.com/signed-url"));
        when(textExtractor.extract(eq("投标文件.pdf"), eq("pdf"), any(byte[].class)))
                .thenReturn(new ExtractedTenderDocument("投标文件.pdf", "application/pdf", "提取到的标书正文", 7, "markitdown"));

        String text = lookup.loadText(PROJECT_ID);

        assertThat(text).isEqualTo("提取到的标书正文");
    }

    @Test
    void loadText_fileStorageFallback_successfullyExtractsText() {
        String fileUrl = "/storage/uploads/bid.docx";
        ProjectDocument doc = ProjectDocument.builder()
                .id(2L)
                .projectId(PROJECT_ID)
                .name("投标文件.docx")
                .fileType("docx")
                .fileUrl(fileUrl)
                .build();

        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of(doc));
        when(fileStorage.load(fileUrl))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(fileUrl, fileUrl, "docx", "本地存储内容".getBytes(StandardCharsets.UTF_8), null)));
        when(textExtractor.extract(eq("投标文件.docx"), eq("docx"), any(byte[].class)))
                .thenReturn(new ExtractedTenderDocument("投标文件.docx", "application/docx", "本地提取正文", 6, "markitdown"));

        String text = lookup.loadText(PROJECT_ID);

        assertThat(text).isEqualTo("本地提取正文");
    }

    @Test
    void loadText_tenderDocumentStorageFallback_successfullyExtractsText() {
        String fileUrl = "doc-insight://bid/tender.pdf";
        ProjectDocument doc = ProjectDocument.builder()
                .id(3L)
                .projectId(PROJECT_ID)
                .name("旧标书.pdf")
                .fileType("pdf")
                .fileUrl(fileUrl)
                .build();

        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of(doc));
        when(documentStorage.loadByFileUrl(fileUrl))
                .thenReturn(Optional.of(new LoadedTenderDocument(
                        new StoredTenderDocument(fileUrl, "/tmp/tender.pdf", "sha256"),
                        "doc-insight内容".getBytes(StandardCharsets.UTF_8))));
        when(textExtractor.extract(eq("旧标书.pdf"), eq("pdf"), any(byte[].class)))
                .thenReturn(new ExtractedTenderDocument("旧标书.pdf", "application/pdf", "doc-insight提取正文", 12, "markitdown"));

        String text = lookup.loadText(PROJECT_ID);

        assertThat(text).isEqualTo("doc-insight提取正文");
    }

    @Test
    void loadText_noDocumentFound_throwsException() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID_FILE", null, null))
                .thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID_DOCUMENT", null, null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> lookup.loadText(PROJECT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("投标文件不存在");
    }

    @Test
    void loadBytes_obsDirectOversizedFile_throwsOversizedException() {
        // 复现 OOM 根因：BoundedHttpDownloader 检出 Content-Length/流式超限时抛 TOO_LARGE，
        // lookup 必须转成 OversizedBidFileException 立即中止，不能吞掉走 fallback
        lookup = new ScoreBidDocumentLookup(
                projectDocumentRepository, documentStorage, textExtractor,
                fileStorage, obsShareUrlSigner,
                url -> { throw new IllegalStateException(BoundedHttpDownloader.TOO_LARGE_MESSAGE); });
        when(obsShareUrlSigner.trySign("obs-direct:f2a75682-f248-4867"))
                .thenReturn(Optional.of("https://obs.huaweicloud.com/signed-url"));

        assertThatThrownBy(() -> lookup.loadBytes("obs-direct:f2a75682-f248-4867"))
                .isInstanceOf(OversizedBidFileException.class)
                .hasMessageContaining("投标文件超过 50MB");
    }

    @Test
    void loadBytes_localStorageOversizedContent_throwsOversizedException() {
        // 本地/挂载存储同样有 50MB 上限，防止换条链路继续 OOM
        when(fileStorage.load("/storage/oversized.pdf"))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(
                        "/storage/oversized.pdf", "/storage/oversized.pdf", "pdf",
                        new byte[BoundedHttpDownloader.MAX_BYTES + 1], null)));

        assertThatThrownBy(() -> lookup.loadBytes("/storage/oversized.pdf"))
                .isInstanceOf(OversizedBidFileException.class)
                .hasMessageContaining("投标文件超过 50MB");
    }
}
