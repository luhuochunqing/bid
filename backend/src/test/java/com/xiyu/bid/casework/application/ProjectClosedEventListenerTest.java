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
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.projectworkflow.repository.ProjectScoreDraftRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

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
    private ProjectScoreDraftRepository scoreDraftRepository;
    private KnowledgeCaseRepository knowledgeCaseRepository;
    private ProjectRepository projectRepository;
    private TenderRepository tenderRepository;
    private BidResultFetchResultRepository bidResultRepository;
    private DocumentTextExtractor docTextExtractor;
    private CaseAiMatcher caseAiMatcher;
    private CasePrecipitationNotifier notifier;
    private ProjectClosedEventListener listener;

    @BeforeEach
    void setUp() {
        archiveRepository = mock(ProjectArchiveRepository.class);
        fileRepository = mock(ArchiveFileRepository.class);
        scoreDraftRepository = mock(ProjectScoreDraftRepository.class);
        knowledgeCaseRepository = mock(KnowledgeCaseRepository.class);
        projectRepository = mock(ProjectRepository.class);
        tenderRepository = mock(TenderRepository.class);
        bidResultRepository = mock(BidResultFetchResultRepository.class);
        docTextExtractor = mock(DocumentTextExtractor.class);
        caseAiMatcher = mock(CaseAiMatcher.class);
        notifier = mock(CasePrecipitationNotifier.class);
        listener = new ProjectClosedEventListener(
                archiveRepository, fileRepository, scoreDraftRepository,
                knowledgeCaseRepository, projectRepository, tenderRepository,
                bidResultRepository, docTextExtractor, caseAiMatcher, notifier);
    }

    @Test
    @DisplayName("前置条件：缺少标书文件 → notifyFailure + 不入库")
    void noBidFile_triggersFailureNotification() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(1L))
                .thenReturn(List.of(draft(101L, "技术方案", "要求提供架构图")));

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        verify(notifier, times(1)).notifyFailure(anyLong(), any(), any(), any());
        verify(knowledgeCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("前置条件：缺少评分项 → notifyFailure + 不入库")
    void noScoreItems_triggersFailureNotification() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        ProjectArchive archive = new ProjectArchive();
        archive.setId(99L);
        when(archiveRepository.findByProjectId(1L)).thenReturn(Optional.of(archive));
        when(fileRepository.findByArchiveId(99L)).thenReturn(List.of(bidFile(1L, 99L)));
        when(scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(1L))
                .thenReturn(List.of());

        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        verify(notifier, times(1)).notifyFailure(anyLong(), any(), any(), any());
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
        List<ProjectScoreDraft> drafts = List.of(
                draft(101L, "技术方案", "要求提供架构图"),
                draft(102L, "商务条款", "要求提供报价明细"));
        when(scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(1L))
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
        verify(notifier, times(1)).notifySuccess(anyLong(), any(), anyInt(), anyLong(), any());
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
        when(scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(1L))
                .thenReturn(List.of(draft(101L, "技术方案", "要求提供架构图")));
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
        when(scoreDraftRepository.findByProjectIdOrderByCategoryAscSourceTableIndexAscSourceRowIndexAsc(1L))
                .thenReturn(List.of(draft(101L, "技术方案", "要求提供架构图")));
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
        // 把异常抛在 try 块内 — archiveRepository.findByProjectId 是 try 块的第一行
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L, 7L, "E2E 项目")));
        when(archiveRepository.findByProjectId(1L))
                .thenThrow(new RuntimeException("DB 故障"));

        // 不应抛异常
        listener.onProjectClosed(new ProjectClosedEvent(this, 1L, "E2E 项目"));

        verify(notifier, times(1)).notifyFailure(anyLong(), any(), any(), any());
    }

    private Project project(Long id, Long managerId, String name) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setManagerId(managerId);
        p.setTenderId(11L);
        return p;
    }

    private ProjectScoreDraft draft(Long id, String title, String rule) {
        ProjectScoreDraft d = new ProjectScoreDraft();
        d.setId(id);
        d.setScoreItemTitle(title);
        d.setScoreRuleText(rule);
        d.setCategory("技术评分");
        return d;
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
