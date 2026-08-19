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
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectClosedEventListener 单元测试。
 *
 * <p>蓝图 4.1.1.2.1 — 6 步异步任务模型的入口监听器：
 * <ol>
 *   <li>前置条件硬校验（缺少标书文件 / 评分项 → notifyFailure，不入库）</li>
 *   <li>正常流程：loadBidFileMarkdown → caseAiMatcher.extractSlicesWithAi → saveAll + notifySuccess</li>
 *   <li>字段语义：industry ← tender.industry；productLine ← project.sourceModule（不走 tender.projectType 兜底）</li>
 *   <li>处理异常：notifyFailure + 捕获异常防止吞掉主流程</li>
 * </ol>
 */
class ProjectClosedEventListenerTest {

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
    private RestTemplate restTemplate;
    private ProjectClosedEventListener listener;

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
        restTemplate = mock(RestTemplate.class);
        listener = new ProjectClosedEventListener(
                archiveRepository, fileRepository, scoreItemRepository,
                knowledgeCaseRepository, projectRepository, tenderRepository,
                bidResultRepository, docTextExtractor, caseAiMatcher, notifier,
                obsShareUrlSigner, restTemplate);
    }

    @Test
    @DisplayName("前置条件：缺少标书文件 -> notifyFailure(原因含'缺少标书文件') + 不入库")
    void noBidFile_triggersFailureNotification() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("缺少标书文件");
        verify(knowledgeCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("前置条件：缺少评分项 -> notifyFailure(原因含'缺少评分项') + 不入库")
    void noScoreItems_triggersFailureNotification() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("缺少评分项");
        verify(knowledgeCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("正常流程：BID 文件 + 评分项齐备 → saveAll + notifySuccess(count=N)")
    void happyPath_savesAndNotifies() {
        Project project = project(1L, 7L, "E2E 项目");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        ArchiveFile bidFile = bidFile(1L, 99L);
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile));
        List<ScoreItem> drafts = List.of(
                scoreItem(101L, "技术方案", "要求提供架构图"),
                scoreItem(102L, "商务条款", "要求提供报价明细"));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(drafts);
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        when(caseAiMatcher.extractSlicesWithAi(any(), any())).thenReturn(List.of(
                slice(101L, "技术方案证明片段", 0.9),
                slice(102L, "商务条款证明片段", 0.85)));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术", "商务");
        when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        when(bidResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                anyLong(), any())).thenReturn(Optional.empty());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeCase>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeCaseRepository, times(1)).saveAll(captor.capture());
        List<KnowledgeCase> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getSourceProjectId()).isEqualTo(1L);
        assertThat(saved.get(0).getRequirementRaw()).isEqualTo("要求提供架构图");
        assertThat(saved.get(0).getResponseText()).isEqualTo("技术方案证明片段");
        assertThat(saved.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.get(0).getScoringCategory()).isIn("技术", "商务");
        verify(notifier, times(1)).notifySuccess(anyLong(), any(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("字段语义：industry 用 tender.industry；productLine 用 project.sourceModule")
    void fieldSemantics_industryFromTender_productLineFromSourceModule() {
        Project project = project(1L, 7L, "E2E 项目");
        project.setIndustry("能源");
        project.setSourceModule("智慧能源产品线");
        project.setCustomerType("民营企业");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenReturn(List.of(slice(101L, "片段", 0.9)));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术");

        Tender tender = new Tender();
        tender.setId(11L);
        tender.setIndustry("能源行业");
        // 注意：即使 tender.projectType 存在，也不应该被当作 industry 或 productLine
        tender.setProjectType("DECOY_PROJECT_TYPE");
        when(tenderRepository.findById(11L)).thenReturn(Optional.of(tender));
        when(bidResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                anyLong(), any())).thenReturn(Optional.empty());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeCase>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeCaseRepository, times(1)).saveAll(captor.capture());
        KnowledgeCase saved = captor.getValue().get(0);
        // 关键断言：industry 来自 tender.industry，不是 tender.projectType
        assertThat(saved.getProjectType()).isEqualTo("能源行业");
        // 关键断言：productLine 来自 project.sourceModule，不是 tender.projectType
        assertThat(saved.getProductLine()).isEqualTo("智慧能源产品线");
    }

    @Test
    @DisplayName("tender.industry 缺失时回退到 project.industry")
    void fieldSemantics_fallbackToProjectIndustry() {
        Project project = project(1L, 7L, "E2E 项目");
        project.setIndustry("交通");
        project.setSourceModule("智慧交通");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenReturn(List.of(slice(101L, "片段", 0.9)));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术");

        Tender tender = new Tender();
        tender.setId(11L);
        // industry 为 null，应回退到 project.industry
        tender.setIndustry(null);
        tender.setProjectType("DO_NOT_USE");
        when(tenderRepository.findById(11L)).thenReturn(Optional.of(tender));
        when(bidResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                anyLong(), any())).thenReturn(Optional.empty());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeCase>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeCaseRepository, times(1)).saveAll(captor.capture());
        KnowledgeCase saved = captor.getValue().get(0);
        assertThat(saved.getProjectType()).isEqualTo("交通");
        assertThat(saved.getProductLine()).isEqualTo("智慧交通");
    }

    @Test
    @DisplayName("处理异常时调用 notifyFailure + 不向上抛")
    void exceptionTriggersFailureNotification() {
        // 把异常抛在 try 块内 - archiveRepository.findByProjectId 是 try 块的第一行
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        when(archiveRepository.findByProjectId(1L))
                .thenThrow(new RuntimeException("DB 故障"));

        // 不应抛异常
        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        verify(notifier, times(1)).notifyFailure(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("第 5 步 AI 提取失败 -> notifyFailure(原因含'AI 案例沉淀失败') + 不入库")
    void aiExtractionFailure_triggersFailureNotification() {
        Project project = project(1L, 7L, "E2E 项目");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        List<ScoreItem> drafts = List.of(scoreItem(101L, "技术方案", "要求提供架构图"));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(drafts);
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        // 模拟 AI 调用抛异常
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenThrow(new IllegalStateException("AI 案例沉淀失败：大模型调用失败，原因：connection timeout"));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("AI 案例沉淀失败");
        assertThat(reasonCaptor.getValue()).contains("大模型调用失败");
        verify(knowledgeCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("第 5 步 AI 提取失败 -> 不向上抛异常（Listener 吞异常防崩主流程）")
    void aiExtractionFailure_doesNotPropagateException() {
        Project project = project(1L, 7L, "E2E 项目");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenThrow(new IllegalStateException("AI 案例沉淀失败：大模型调用失败"));

        // 不应抛异常 - Listener 必须吞掉异常，只发通知
        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        verify(notifier, times(1)).notifyFailure(anyLong(), any(), any(), any(), any());
        verify(knowledgeCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("重复结项事件时自动去重，不重复持久化已有评分项案例")
    void onProjectClosed_WhenCasesAlreadyExist_ShouldDeduplicateAndNotSaveDuplicates() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        CaseAiMatcher.AiMatchedSlice slice = new CaseAiMatcher.AiMatchedSlice();
        slice.setDraftId(101L);
        slice.setMatchedSnippet("架构图已提供");
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenReturn(List.of(slice));

        KnowledgeCase existing = new KnowledgeCase();
        existing.setId(50L);
        existing.setSourceProjectId(1L);
        existing.setScoringPointTitle("技术方案");
        when(knowledgeCaseRepository.findBySourceProjectId(1L)).thenReturn(List.of(existing));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        // 已经存在，不应该调用 saveAll
        verify(knowledgeCaseRepository, never()).saveAll(any());
        verify(notifier, times(1)).notifySuccess(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq(0), anyLong(), any(), any());
    }

    @Test
    @DisplayName("并发结项抛出 DataIntegrityViolationException 时应作为幂等成功处理并发送成功通知")
    void onProjectClosed_WhenConcurrentUniqueViolationOccurs_ShouldHandleAsIdempotentSuccess() {
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文", 5, "text/markdown", "bid.pdf", java.util.Map.of()));
        CaseAiMatcher.AiMatchedSlice slice = new CaseAiMatcher.AiMatchedSlice();
        slice.setDraftId(101L);
        slice.setMatchedSnippet("架构图已提供");
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenReturn(List.of(slice));
        when(knowledgeCaseRepository.findBySourceProjectId(1L)).thenReturn(List.of());
        when(knowledgeCaseRepository.saveAll(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        verify(notifier, times(1)).notifySuccess(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq(0), anyLong(), any(), any());
        verify(notifier, never()).notifyFailure(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OBS 直传标书：签名下载成功 -> 解析正文并保存案例")
    void obsBidFile_downloadAndSave() {
        Project project = project(1L, 7L, "E2E 项目");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        ArchiveFile obsBid = bidFile(1L, 99L);
        obsBid.setFilePath(FileUrlPrefixes.OBS_DIRECT + "upload123");
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(obsBid));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(obsShareUrlSigner.trySign(FileUrlPrefixes.OBS_DIRECT + "upload123"))
                .thenReturn(Optional.of("https://obs.example.com/signed"));
        when(restTemplate.getForObject(URI.create("https://obs.example.com/signed"), byte[].class))
                .thenReturn("PDF_BYTES".getBytes());
        when(docTextExtractor.extract(any(), any(), any()))
                .thenReturn(new ExtractedDocument("## 标书正文(obs)", 5, "text/markdown", "bid.pdf",
                        java.util.Map.of()));
        when(caseAiMatcher.extractSlicesWithAi(any(), any()))
                .thenReturn(List.of(slice(101L, "技术方案证明片段", 0.9)));
        when(caseAiMatcher.extractCategory(any())).thenReturn("技术");
        when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        when(bidResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                anyLong(), any())).thenReturn(Optional.empty());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        // 必须走签名下载，而不是把 obs-direct 当本地文件读；注意传的是 URI 而非 String
        verify(restTemplate).getForObject(URI.create("https://obs.example.com/signed"), byte[].class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeCase>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeCaseRepository, times(1)).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        verify(notifier, times(1)).notifySuccess(anyLong(), any(), anyInt(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("OBS 标书签名失败 -> notifyFailure(原因含 OBS) + 不入库")
    void obsBidFile_signFailure_notifiesFailure() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        ArchiveFile obsBid = bidFile(1L, 99L);
        obsBid.setFilePath(FileUrlPrefixes.OBS_DIRECT + "upload123");
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(obsBid));
        when(scoreItemRepository.findByProjectIdOrderByItemIndexAsc(1L))
                .thenReturn(List.of(scoreItem(101L, "技术方案", "要求提供架构图")));
        when(obsShareUrlSigner.trySign(FileUrlPrefixes.OBS_DIRECT + "upload123"))
                .thenReturn(Optional.empty());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(1)).notifyFailure(anyLong(), any(), reasonCaptor.capture(), any(), any());
        assertThat(reasonCaptor.getValue()).contains("OBS");
        verify(knowledgeCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("根因回归：OBS 预签名 URL 含 % 编码时下载不得二次编码（RestTemplate 传 URI，内嵌真实服务器）")
    void obsPresignedUrl_notDoubleEncoded_rootCauseRegression() throws Exception {
        // 内嵌真实 HTTP 服务器，记录「实际到达的原始请求路径」，返回 200 + 字节。
        // 不 mock 网络层——mock 会掩盖 RestTemplate 对预签名 URL 的二次编码行为（之前的 403 根因）。
        AtomicReference<String> rawPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            rawPath.set(exchange.getRequestURI().getRawPath());
            byte[] body = "PDF_BYTES".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            // 模拟 OBS 预签名 URL：路径已含一次 percent 编码（%E5...）。若被二次编码会变 %25E5，
            // 触发 SignatureDoesNotMatch(403)。签名参数同样含 %3D。
            String signedUrl = "http://127.0.0.1:" + port
                    + "/bids/2026/08/u/%E5%BC%A0%E5%AE%B6%E5%8F%A3.docx?Expires=1787155399&Signature=abc%3D";

            ObsShareUrlSigner signer = mock(ObsShareUrlSigner.class);
            when(signer.trySign(FileUrlPrefixes.OBS_DIRECT + "upload123"))
                    .thenReturn(Optional.of(signedUrl));

            RestTemplate realRest = new RestTemplate(new SimpleClientHttpRequestFactory());
            ProjectClosedEventListener underTest = new ProjectClosedEventListener(
                    archiveRepository, fileRepository, scoreItemRepository,
                    knowledgeCaseRepository, projectRepository, tenderRepository,
                    bidResultRepository, docTextExtractor, caseAiMatcher, notifier,
                    signer, realRest);

            byte[] bytes = underTest.loadFileBytes(FileUrlPrefixes.OBS_DIRECT + "upload123");

            assertThat(bytes).isEqualTo("PDF_BYTES".getBytes());
            // 关键断言：服务器收到的路径保持单次编码（%E5...），未被二次编码成 %25E5
            assertThat(rawPath.get()).contains("%E5%BC%A0%E5%AE%B6%E5%8F%A3");
            assertThat(rawPath.get()).doesNotContain("%25E5");
        } finally {
            server.stop(0);
        }
    }

    private Project project(Long id, Long managerId, String name) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setManagerId(managerId);
        p.setTenderId(11L);
        return p;
    }

    private ScoreItem scoreItem(Long id, String dim, String detail) {
        return ScoreItem.builder().id(id).dim(dim).detail(detail).build();
    }

    private ArchiveFile bidFile(Long id, Long archiveId) {
        ArchiveFile f = new ArchiveFile();
        f.setId(id);
        f.setArchiveId(archiveId);
        f.setFileName("bid.pdf");
        f.setDocumentCategory("BID");
        f.setFilePath("/tmp/placeholder-bid.pdf");
        f.setFileSize(1024L);
        f.setUploadUserId(7L);
        f.setUploadUserName("uploader");
        return f;
    }

    private CaseAiMatcher.AiMatchedSlice slice(Long draftId, String text, double confidence) {
        CaseAiMatcher.AiMatchedSlice s = new CaseAiMatcher.AiMatchedSlice();
        s.setDraftId(draftId);
        s.setMatchedSnippet(text);
        s.setConfidence(confidence);
        return s;
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
