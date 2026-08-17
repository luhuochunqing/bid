// Input: projectId + taskId（mock 存储与流式 fetcher）
// Output: loadBidFile 二态断言（InMemory 快路径 / OnDisk spill / 配额即拒 / IO 降级）
// Pos: scoreparse/application — spec 045 T012：capSize 断言废止，改二态断言
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

@ExtendWith(MockitoExtension.class)
class ScoreBidDocumentLookupTest {

    private static final Long PROJECT_ID = 100L;
    private static final String TASK_ID = "task-uuid";

    @Mock
    private ProjectDocumentRepository projectDocumentRepository;
    @Mock
    private TenderDocumentStorage documentStorage;
    @Mock
    private ProjectDocumentFileStorage fileStorage;
    @Mock
    private ObsShareUrlSigner obsShareUrlSigner;
    @Mock
    private StreamingBidFileFetcher streamingFetcher;

    private ScoreBidDocumentLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new ScoreBidDocumentLookup(
                projectDocumentRepository, documentStorage, fileStorage,
                obsShareUrlSigner, streamingFetcher);
    }

    @Test
    void loadBidFile_obsDirectSmallFile_returnsInMemory() throws Exception {
        String fileUrl = "obs-direct:bid/project100/sample.pdf";
        mockBidDocument(fileUrl);
        byte[] content = "OBS文件内容".getBytes(StandardCharsets.UTF_8);
        when(obsShareUrlSigner.trySign(fileUrl)).thenReturn(Optional.of("https://obs/signed-url"));
        when(streamingFetcher.fetch(eq("https://obs/signed-url"), eq(TASK_ID), eq("投标文件.pdf"), eq("pdf")))
                .thenReturn(new LoadedBidFile.InMemory(content, "投标文件.pdf", "pdf"));

        LoadedBidFile loaded = lookup.loadBidFile(PROJECT_ID, TASK_ID);

        assertThat(loaded).isInstanceOf(LoadedBidFile.InMemory.class);
        assertThat(((LoadedBidFile.InMemory) loaded).content()).isEqualTo(content);
        assertThat(loaded.fileName()).isEqualTo("投标文件.pdf");
    }

    @Test
    void loadBidFile_obsDirectSpilledFile_returnsOnDisk() throws Exception {
        String fileUrl = "obs-direct:bid/project100/big.pdf";
        mockBidDocument(fileUrl);
        Path spill = Path.of("/tmp/xiyu-score-parse", TASK_ID + ".part");
        when(obsShareUrlSigner.trySign(fileUrl)).thenReturn(Optional.of("https://obs/signed-url"));
        when(streamingFetcher.fetch(anyString(), eq(TASK_ID), anyString(), anyString()))
                .thenReturn(new LoadedBidFile.OnDisk(spill, "投标文件.pdf", "pdf"));

        LoadedBidFile loaded = lookup.loadBidFile(PROJECT_ID, TASK_ID);

        assertThat(loaded).isInstanceOf(LoadedBidFile.OnDisk.class);
        assertThat(((LoadedBidFile.OnDisk) loaded).tempFile()).isEqualTo(spill);
    }

    @Test
    void loadBidFile_obsDirectQuotaExceeded_propagatesWithoutFallback() throws Exception {
        // 配额拒绝必须立即中止（FR-002），不能吞掉换本地链路重试
        String fileUrl = "obs-direct:bid/project100/huge.pdf";
        mockBidDocument(fileUrl);
        when(obsShareUrlSigner.trySign(fileUrl)).thenReturn(Optional.of("https://obs/signed-url"));
        when(streamingFetcher.fetch(anyString(), eq(TASK_ID), anyString(), anyString()))
                .thenThrow(new BidFileQuotaException());

        assertThatThrownBy(() -> lookup.loadBidFile(PROJECT_ID, TASK_ID))
                .isInstanceOf(BidFileQuotaException.class)
                .hasMessageContaining("配额");
        verify(fileStorage, never()).load(anyString());
    }

    @Test
    void loadBidFile_obsDirectIoError_fallsBackToLocal() throws Exception {
        // OBS 下载抖动（签名过期/网络断）降级本地链路，不让任务直接失败
        String fileUrl = "obs-direct:bid/project100/flaky.pdf";
        mockBidDocument(fileUrl);
        byte[] content = "本地内容".getBytes(StandardCharsets.UTF_8);
        when(obsShareUrlSigner.trySign(fileUrl)).thenReturn(Optional.of("https://obs/signed-url"));
        when(streamingFetcher.fetch(anyString(), eq(TASK_ID), anyString(), anyString()))
                .thenThrow(new IOException("connection reset"));
        when(fileStorage.load(fileUrl))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(fileUrl, fileUrl, "pdf", content, null)));

        LoadedBidFile loaded = lookup.loadBidFile(PROJECT_ID, TASK_ID);

        assertThat(loaded).isInstanceOf(LoadedBidFile.InMemory.class);
        assertThat(((LoadedBidFile.InMemory) loaded).content()).isEqualTo(content);
    }

    @Test
    void loadBidFile_fileStorageFallback_returnsInMemory() {
        String fileUrl = "/storage/uploads/bid.docx";
        mockBidDocument(fileUrl);
        byte[] content = "本地存储内容".getBytes(StandardCharsets.UTF_8);
        when(fileStorage.load(fileUrl))
                .thenReturn(Optional.of(new LoadedProjectDocumentFile(fileUrl, fileUrl, "docx", content, null)));

        LoadedBidFile loaded = lookup.loadBidFile(PROJECT_ID, TASK_ID);

        assertThat(loaded).isInstanceOf(LoadedBidFile.InMemory.class);
        assertThat(((LoadedBidFile.InMemory) loaded).content()).isEqualTo(content);
    }

    @Test
    void loadBidFile_tenderDocumentStorageFallback_returnsInMemory() {
        String fileUrl = "doc-insight://bid/tender.pdf";
        mockBidDocument(fileUrl);
        byte[] content = "doc-insight内容".getBytes(StandardCharsets.UTF_8);
        when(documentStorage.loadByFileUrl(fileUrl))
                .thenReturn(Optional.of(new LoadedTenderDocument(
                        new StoredTenderDocument(fileUrl, "/tmp/tender.pdf", "sha256"), content)));

        LoadedBidFile loaded = lookup.loadBidFile(PROJECT_ID, TASK_ID);

        assertThat(loaded).isInstanceOf(LoadedBidFile.InMemory.class);
        assertThat(((LoadedBidFile.InMemory) loaded).content()).isEqualTo(content);
    }

    @Test
    void loadBidFile_noDocumentFound_throwsException() {
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID_FILE", null, null))
                .thenReturn(List.of());
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID_DOCUMENT", null, null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> lookup.loadBidFile(PROJECT_ID, TASK_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("投标文件不存在");
    }

    @Test
    void loadBidFile_blankFileUrl_throwsException() {
        mockBidDocument(" ");

        assertThatThrownBy(() -> lookup.loadBidFile(PROJECT_ID, TASK_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fileUrl 为空");
    }

    private void mockBidDocument(String fileUrl) {
        ProjectDocument doc = ProjectDocument.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .name("投标文件.pdf")
                .fileType("pdf")
                .fileUrl(fileUrl)
                .build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of(doc));
    }
}
