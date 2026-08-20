// Input: OBS 标书流式加载二态分派（InMemory 快路径 / OnDisk 流式）
// Output: 提取文本入库 / finally release / 失败 rethrow + notifyFailure / 事务只包落库
// Pos: casework/application — ProjectClosedEventListener 流式接入单测（spec 049 T006）
package com.xiyu.bid.casework.application;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.casework.infrastructure.ArchiveFile;
import com.xiyu.bid.casework.infrastructure.ArchiveFileRepository;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.casework.infrastructure.ProjectArchive;
import com.xiyu.bid.casework.infrastructure.ProjectArchiveRepository;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import com.xiyu.bid.file.application.ObsShareUrlSigner;
import com.xiyu.bid.file.domain.FileUrlPrefixes;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.scoreparse.application.LoadedBidFile;
import com.xiyu.bid.scoreparse.application.SidecarStreamTextExtractor;
import com.xiyu.bid.scoreparse.application.StreamingBidFileFetcher;
import com.xiyu.bid.scoreparse.application.TempBidFileStore;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * ProjectClosedEventListener 流式接入单元测试（spec 049 Phase 2）。
 *
 * <p>覆盖 OBS 标书加载的二态分派与资源释放、失败语义、事务边界：
 * <ul>
 *   <li>InMemory 快路径：≤50MB 走 docTextExtractor（行为与现网一致）</li>
 *   <li>OnDisk 流式：spill 落盘走 sidecar /convert-stream，零整包进堆</li>
 *   <li>finally release：成功/失败都归还 TempBidFileStore 配额</li>
 *   <li>事务只包 saveAll 落库：下载/转换/LLM 均在事务外</li>
 * </ul>
 */
class ProjectClosedEventListenerStreamingTest {

    private ProjectArchiveRepository archiveRepository;
    private ArchiveFileRepository fileRepository;
    private ScoreItemRepository scoreItemRepository;
    private KnowledgeCaseRepository knowledgeCaseRepository;
    private ProjectRepository projectRepository;
    private TenderRepository tenderRepository;
    private BidResultFetchResultRepository bidResultRepository;
    private DocumentTextExtractor docTextExtractor;
    private CaseAiMatcher caseAiMatcher;
    private CasePrecipitationNotifier notifier;
    private ObsShareUrlSigner obsShareUrlSigner;
    private StreamingBidFileFetcher streamingFetcher;
    private SidecarStreamTextExtractor streamTextExtractor;
    private TempBidFileStore tempBidFileStore;
    private TransactionTemplate transactionTemplate;
    private PlatformTransactionManager txManager;
    private ProjectClosedEventListener listener;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        archiveRepository = mock(ProjectArchiveRepository.class);
        fileRepository = mock(ArchiveFileRepository.class);
        scoreItemRepository = mock(ScoreItemRepository.class);
        knowledgeCaseRepository = mock(KnowledgeCaseRepository.class);
        projectRepository = mock(ProjectRepository.class);
        tenderRepository = mock(TenderRepository.class);
        bidResultRepository = mock(BidResultFetchResultRepository.class);
        docTextExtractor = mock(DocumentTextExtractor.class);
        caseAiMatcher = mock(CaseAiMatcher.class);
        notifier = mock(CasePrecipitationNotifier.class);
        obsShareUrlSigner = mock(ObsShareUrlSigner.class);
        streamingFetcher = mock(StreamingBidFileFetcher.class);
        streamTextExtractor = mock(SidecarStreamTextExtractor.class);
        tempBidFileStore = mock(TempBidFileStore.class);
        // 真实 TransactionTemplate + mock PlatformTransactionManager：execute 回调真实执行（saveAll 生效），
        // 避免 Mockito 对接口 default 方法 executeWithoutResult 的 mock 行为差异
        txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        transactionTemplate = new TransactionTemplate(txManager);
        listener = new ProjectClosedEventListener(
                archiveRepository, fileRepository, scoreItemRepository,
                knowledgeCaseRepository, projectRepository, tenderRepository,
                bidResultRepository, docTextExtractor, caseAiMatcher, notifier,
                obsShareUrlSigner, streamingFetcher, streamTextExtractor,
                tempBidFileStore, transactionTemplate);

        // 公共前置：BID 文件 + 评分项齐备，签名成功
        Project project = new Project();
        project.setId(1L);
        project.setManagerId(7L);
        project.setTenderId(11L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        ArchiveFile obsBid = new ArchiveFile();
        obsBid.setId(1L);
        obsBid.setArchiveId(99L);
        obsBid.setFileName("bid.pdf");
        obsBid.setDocumentCategory("BID");
        obsBid.setFilePath(FileUrlPrefixes.OBS_DIRECT + "upload123");
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(obsBid));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(ScoreItem.builder().id(101L).dim("技术方案").detail("要求提供架构图").build()));
        when(obsShareUrlSigner.trySign(FileUrlPrefixes.OBS_DIRECT + "upload123"))
                .thenReturn(Optional.of("https://obs.example.com/signed"));
        when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        when(bidResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                anyLong(), any())).thenReturn(Optional.empty());
    }

    private CaseAiMatcher.AiMatchedSlice slice(Long draftId, String text) {
        CaseAiMatcher.AiMatchedSlice s = new CaseAiMatcher.AiMatchedSlice();
        s.setDraftId(draftId);
        s.setMatchedSnippet(text);
        s.setConfidence(0.9);
        return s;
    }

    @Test
    @DisplayName("OBS 小文件 -> InMemory 快路径 -> docTextExtractor 提取入库")
    void inMemoryFastPath_extractsViaDocTextExtractor() throws Exception {
        when(streamingFetcher.fetch("https://obs.example.com/signed", "case-1", "bid.pdf", "application/octet-stream"))
                .thenReturn(new LoadedBidFile.InMemory("PDF_BYTES".getBytes(), "bid.pdf", "application/octet-stream"));
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        when(caseAiMatcher.extractSlicesWithAi(any(), any())).thenReturn(List.of(slice(101L, "技术方案证明片段")));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术");

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        // 快路径：InMemory 内容交给 docTextExtractor，不走 sidecar 流式
        verify(streamingFetcher).fetch("https://obs.example.com/signed", "case-1", "bid.pdf", "application/octet-stream");
        verify(docTextExtractor, times(1)).extract(any(), any(), any());
        verify(streamTextExtractor, never()).extract(any(), any());
        verify(knowledgeCaseRepository, times(1)).saveAll(any());
        verify(notifier, times(1)).notifySuccess(anyLong(), any(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("OBS 大文件 -> OnDisk 流式 -> sidecar 转换入库，零整包进堆")
    void onDiskStreaming_extractsViaSidecar() throws Exception {
        Path spillPath = tempDir.resolve("case-1.part");
        when(streamingFetcher.fetch(any(), any(), any(), any()))
                .thenReturn(new LoadedBidFile.OnDisk(spillPath, "bid.pdf", "application/octet-stream"));
        when(streamTextExtractor.extract(spillPath, "bid.pdf")).thenReturn("## 流式提取的标书正文");
        when(caseAiMatcher.extractSlicesWithAi(any(), any())).thenReturn(List.of(slice(101L, "技术方案证明片段")));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术");

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        // OnDisk：走 sidecar 流式转换，不读 InMemory、不经过 docTextExtractor
        verify(streamTextExtractor, times(1)).extract(spillPath, "bid.pdf");
        verify(docTextExtractor, never()).extract(any(), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeCase>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeCaseRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getResponseText()).isEqualTo("技术方案证明片段");
        // finally：OnDisk 临时文件必须 release 归还配额
        verify(tempBidFileStore, times(1)).release("case-1");
    }

    @Test
    @DisplayName("OBS 流式下载失败 -> notifyFailure(原因含 OBS) + 不入库 + finally release")
    void downloadFailure_notifiesFailureAndReleases() throws Exception {
        when(streamingFetcher.fetch(any(), any(), any(), any()))
                .thenThrow(new IOException("网络中断"));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("流式下载 OBS 标书失败");
        verify(knowledgeCaseRepository, never()).saveAll(any());
        verify(tempBidFileStore, times(1)).release("case-1");
    }

    @Test
    @DisplayName("OBS OnDisk 流式提取失败(Runtime 透传) -> notifyFailure 原样透出原因 + 不入库 + finally release")
    void onDiskExtractFailure_notifiesFailureAndReleases() throws Exception {
        when(streamingFetcher.fetch(any(), any(), any(), any()))
                .thenReturn(new LoadedBidFile.OnDisk(tempDir.resolve("case-1.part"), "bid.pdf", "application/octet-stream"));
        when(streamTextExtractor.extract(any(), any()))
                .thenThrow(new IllegalStateException("文档无法解析"));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        // RuntimeException（如 422 解析失败）原样透传，失败原因不丢失
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("文档无法解析");
        verify(knowledgeCaseRepository, never()).saveAll(any());
        verify(tempBidFileStore, times(1)).release("case-1");
    }

    @Test
    @DisplayName("OBS OnDisk 流式提取 checked 异常 -> 包装为『流式提取 OBS 标书文本失败』 + finally release")
    void onDiskCheckedException_wrapsMessage() throws Exception {
        when(streamingFetcher.fetch(any(), any(), any(), any()))
                .thenReturn(new LoadedBidFile.OnDisk(tempDir.resolve("case-1.part"), "bid.pdf", "application/octet-stream"));
        when(streamTextExtractor.extract(any(), any())).thenThrow(new IOException("连接中断"));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("流式提取 OBS 标书文本失败");
        verify(knowledgeCaseRepository, never()).saveAll(any());
        verify(tempBidFileStore, times(1)).release("case-1");
    }

    @Test
    @DisplayName("OBS 文本量超限(Runtime 透传) -> notifyFailure 原样透出原因 + 不入库")
    void oversizedText_notifiesFailure() throws Exception {
        when(streamingFetcher.fetch(any(), any(), any(), any()))
                .thenReturn(new LoadedBidFile.OnDisk(tempDir.resolve("case-1.part"), "bid.pdf", "application/octet-stream"));
        // 真实链路为 OversizedBidTextException（Runtime，scoreparse 包内包私有构造器，跨包用同级语义模拟）
        when(streamTextExtractor.extract(any(), any()))
                .thenThrow(new IllegalStateException("投标文件文本量超过 500 万字符上限"));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("500 万字符");
        verify(knowledgeCaseRepository, never()).saveAll(any());
        verify(tempBidFileStore, times(1)).release("case-1");
    }

    @Test
    @DisplayName("事务边界：saveAll 经 TransactionTemplate 执行，且 download/convert/LLM 不触发第二次事务")
    void transactionBoundary_saveOnlyWrappedInTransaction() throws Exception {
        when(streamingFetcher.fetch(any(), any(), any(), any()))
                .thenReturn(new LoadedBidFile.OnDisk(tempDir.resolve("case-1.part"), "bid.pdf", "application/octet-stream"));
        when(streamTextExtractor.extract(any(), any())).thenReturn("## 标书正文");
        when(caseAiMatcher.extractSlicesWithAi(any(), any())).thenReturn(List.of(slice(101L, "证明片段")));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术");

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        // 落库只发生一次且必然通过事务回调；流式下载/转换本身不是事务操作
        verify(knowledgeCaseRepository, times(1)).saveAll(any());
        // 事务仅打开一次（saveAll 用）：getTransaction 计数=1 证明下载/转换/LLM 全程无第二个事务
        verify(txManager, times(1)).getTransaction(any());
    }
}
