// Input: projectId + taskId（mock 存储与两个 sidecar 提取器）
// Output: LoadedBidDocument.images 断言（InMemory/OnDisk 双路径抽图 / 失败降级空图 / 开关关闭跳过）
// Pos: scoreparse/application — BidFileLoadingCoordinator 图片接入测试（spec 048 T013，review H2）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidFileLoadingCoordinatorTest {

    @TempDir
    Path tempDir;

    private static final Long PROJECT_ID = 100L;
    private static final String TASK_ID = "task-uuid";
    private static final String FILE_NAME = "投标文件.docx";
    private static final DocumentImage IMAGE =
            new DocumentImage(0, "image/png", "3.2 业绩要求", null, 800, 600, "aGk=");

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
    @Mock
    private StreamingBidFileFetcher streamingFetcher;
    @Mock
    private SidecarStreamTextExtractor streamTextExtractor;
    @Mock
    private TempBidFileStore tempFileStore;
    @Mock
    private SidecarImageExtractor imageExtractor;

    private BidFileLoadingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new BidFileLoadingCoordinator(
                projectDocumentRepository, documentStorage, textExtractor, fileStorage,
                obsShareUrlSigner, streamingFetcher, streamTextExtractor, tempFileStore,
                imageExtractor, true);
    }

    @Test
    void load_inMemory_extractsImagesViaByteArray() throws Exception {
        mockLocalStorageBidDocument();
        byte[] content = "DOCX内容".getBytes(StandardCharsets.UTF_8);
        when(textExtractor.extract(FILE_NAME, "docx", content))
                .thenReturn(new ExtractedTenderDocument(FILE_NAME, "docx", "投标正文", 4, "test"));
        when(imageExtractor.extract(content, FILE_NAME))
                .thenReturn(new SidecarImageExtraction(List.of(IMAGE), false));

        BidFileLoadingCoordinator.LoadedBidDocument loaded = coordinator.load(PROJECT_ID, TASK_ID);

        assertThat(loaded.text()).isEqualTo("投标正文");
        assertThat(loaded.images()).containsExactly(IMAGE);
        // InMemory 全量 byte[] 直传（review H2：内存中已有整包，不再绕道临时文件）
        verify(imageExtractor).extract(content, FILE_NAME);
        verify(imageExtractor, org.mockito.Mockito.never()).extract(any(Path.class), anyString());
    }

    @Test
    void load_onDisk_extractsImagesViaFile() throws Exception {
        Path spill = tempDir.resolve(TASK_ID + ".part");
        java.nio.file.Files.write(spill, "DOCX-BYTES".getBytes(StandardCharsets.UTF_8));
        mockObsBidDocument(spill);
        when(streamTextExtractor.extract(spill, FILE_NAME)).thenReturn("投标正文");
        when(imageExtractor.extract(spill, FILE_NAME))
                .thenReturn(new SidecarImageExtraction(List.of(IMAGE), true));

        BidFileLoadingCoordinator.LoadedBidDocument loaded = coordinator.load(PROJECT_ID, TASK_ID);

        assertThat(loaded.images()).containsExactly(IMAGE);
        // OnDisk 流式上传（ofFile）
        verify(imageExtractor).extract(spill, FILE_NAME);
        verify(tempFileStore).release(TASK_ID);
    }

    @Test
    void load_imageExtractionFails_degradesToEmptyImages() throws Exception {
        mockLocalStorageBidDocument();
        byte[] content = "DOCX内容".getBytes(StandardCharsets.UTF_8);
        when(textExtractor.extract(FILE_NAME, "docx", content))
                .thenReturn(new ExtractedTenderDocument(FILE_NAME, "docx", "投标正文", 4, "test"));
        when(imageExtractor.extract(content, FILE_NAME))
                .thenThrow(new OversizedImageResponseException());

        BidFileLoadingCoordinator.LoadedBidDocument loaded = coordinator.load(PROJECT_ID, TASK_ID);

        // Principle VII：抽图失败/超限降级纯文本，不阻断打分任务
        assertThat(loaded.text()).isEqualTo("投标正文");
        assertThat(loaded.images()).isEmpty();
    }

    @Test
    void load_multimodalDisabled_skipsImageExtraction() throws Exception {
        coordinator = new BidFileLoadingCoordinator(
                projectDocumentRepository, documentStorage, textExtractor, fileStorage,
                obsShareUrlSigner, streamingFetcher, streamTextExtractor, tempFileStore,
                imageExtractor, false);
        mockLocalStorageBidDocument();
        byte[] content = "DOCX内容".getBytes(StandardCharsets.UTF_8);
        when(textExtractor.extract(FILE_NAME, "docx", content))
                .thenReturn(new ExtractedTenderDocument(FILE_NAME, "docx", "投标正文", 4, "test"));

        BidFileLoadingCoordinator.LoadedBidDocument loaded = coordinator.load(PROJECT_ID, TASK_ID);

        // FR-006：全局开关关闭 → 不发起抽图调用（省 sidecar 开销），打分完全纯文本
        assertThat(loaded.text()).isEqualTo("投标正文");
        assertThat(loaded.images()).isEmpty();
        verifyNoInteractions(imageExtractor);
    }

    private void mockLocalStorageBidDocument() {
        String fileUrl = "/storage/uploads/bid.docx";
        mockBidDocument(fileUrl);
        when(fileStorage.load(fileUrl)).thenAnswer(invocation ->
                Optional.of(new LoadedProjectDocumentFile(
                        fileUrl, fileUrl, "docx",
                        "DOCX内容".getBytes(StandardCharsets.UTF_8), null)));
    }

    private void mockObsBidDocument(Path spill) throws IOException, InterruptedException {
        String fileUrl = "obs-direct:bid/project100/big.docx";
        mockBidDocument(fileUrl);
        when(obsShareUrlSigner.trySign(fileUrl)).thenReturn(Optional.of("https://obs/signed-url"));
        when(streamingFetcher.fetch(anyString(), eq(TASK_ID), eq(FILE_NAME), eq("docx")))
                .thenReturn(new LoadedBidFile.OnDisk(spill, FILE_NAME, "docx"));
    }

    private void mockBidDocument(String fileUrl) {
        ProjectDocument doc = ProjectDocument.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .name(FILE_NAME)
                .fileType("docx")
                .fileUrl(fileUrl)
                .build();
        when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(PROJECT_ID, "BID", null, null))
                .thenReturn(List.of(doc));
    }
}
