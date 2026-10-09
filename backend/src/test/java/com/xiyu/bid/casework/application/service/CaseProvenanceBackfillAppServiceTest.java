package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;
import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Update;
import java.util.ArrayList;
import static org.mockito.Mockito.doAnswer;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaseProvenanceBackfillAppServiceTest {

    private static final String MARKDOWN = """
            # 13.2. 对接平台对接成功案例及证明材料

            ## 13.2.1. 对接成功案例清单
            序号客户名称企业类型1启明信息技术股份有限公司API大型企业
            """;

    private static final String TABLE_SNIPPET = "序号客户名称企业类型1启明信息技术股份有限公司API大型企业";

        private static final String UNNUMBERED_MARKDOWN = """
            # 13.2. 对接平台对接成功案例及证明材料

            ## 项目实施
            1启明信息技术股份有限公司API大型企业
            """;

    private CaseProvenanceBackfillPort caseRepository;
    private ProjectRepository projectRepository;
    private BidDocumentLoader bidDocumentLoader;
    private CaseProvenanceBackfillAppService service;

    private final List<Update> writes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseProvenanceBackfillPort.class);
        projectRepository = mock(ProjectRepository.class);
        bidDocumentLoader = mock(BidDocumentLoader.class);
        service = new CaseProvenanceBackfillAppService(caseRepository, projectRepository, bidDocumentLoader);
        doAnswer(invocation -> { writes.addAll(invocation.getArgument(0)); return null; })
                .when(caseRepository).saveAll(anyList());
        when(projectRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    private KnowledgeCase caseOf(Long id, Long projectId, String responseText, String sectionTitle) {
        KnowledgeCase kase = new KnowledgeCase();
        kase.setId(id);
        kase.setSourceProjectId(projectId);
        kase.setResponseText(responseText);
        kase.setScoringPointTitle("对接成功案例");
        kase.setSourceSectionTitle(sectionTitle);
        return kase;
    }

    @Test
    void recompute_writesProvenanceForNullTitleCase() {
        KnowledgeCase target = caseOf(1L, 100L, TABLE_SNIPPET, null);
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-100", 100L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "商务技术标书.pdf", 12, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesScanned()).isEqualTo(1);
        assertThat(result.casesUpdated()).isEqualTo(1);
        assertThat(output(target).sourceSectionTitle()).isEqualTo("13.2.1. 对接成功案例清单");
        assertThat(output(target).sourceSectionPath())
                .isEqualTo("13.2. 对接平台对接成功案例及证明材料 > 13.2.1. 对接成功案例清单");
        assertThat(output(target).sourceSectionIndex()).isEqualTo(2);
        assertThat(output(target).sourceDocumentName()).isEqualTo("商务技术标书.pdf");
        assertThat(output(target).sourcePageNumber()).isEqualTo(12);
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).id()).isEqualTo(target.getId());
        // 写回必须携带扫描快照，供持久化原子校验（过期快照拒绝写入）
        assertThat(writes.get(0).expectedResponseText()).isEqualTo(TABLE_SNIPPET);
        assertThat(writes.get(0).expectedScoringPointTitle()).isEqualTo("对接成功案例");
        assertThat(writes.get(0).expectedSourceProjectId()).isEqualTo(100L);
    }

    @Test
    void recompute_clearsSentenceLikeTitleWhenLocateFails() {
        KnowledgeCase target = caseOf(2L, 200L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-200", 200L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesCleared()).isEqualTo(1);
        assertThat(output(target).sourceSectionTitle()).isNull();
        assertThat(output(target).sourceSectionPath()).isNull();
        assertThat(output(target).sourceSectionIndex()).isNull();
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).id()).isEqualTo(target.getId());
    }

    @Test
    void recompute_replacesSentenceLikeTitle() {
        KnowledgeCase target = caseOf(3L, 300L, TABLE_SNIPPET, "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-300", 300L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesUpdated()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        assertThat(output(target).sourceSectionTitle()).isEqualTo("13.2.1. 对接成功案例清单");
    }

    @Test
    void recompute_countsFailedProject() {
        KnowledgeCase target = caseOf(4L, 400L, TABLE_SNIPPET, null);
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument(anyString(), anyLong()))
                .thenThrow(new IllegalStateException("OBS 签名失败，无法下载标书"));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesUpdated()).isZero();
        assertThat(result.casesCleared()).isZero();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_loadsBidDocumentOncePerProject() {
        KnowledgeCase first = caseOf(5L, 500L, TABLE_SNIPPET, null);
        KnowledgeCase second = caseOf(6L, 500L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(first), snapshot(second)));
        when(bidDocumentLoader.loadBidDocument("项目-500", 500L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        verify(bidDocumentLoader, times(1)).loadBidDocument("项目-500", 500L);
        assertThat(result.projectsScanned()).isEqualTo(1);
        assertThat(result.casesScanned()).isEqualTo(2);
        assertThat(result.casesUpdated()).isEqualTo(1);
        assertThat(result.casesCleared()).isEqualTo(1);
    }

    @Test
    void recompute_skipsCasesWithValidTitle() {
        KnowledgeCase valid = caseOf(7L, 700L, TABLE_SNIPPET, "13.2.1. 对接成功案例清单");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(valid)));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesScanned()).isZero();
        verifyNoInteractions(bidDocumentLoader);
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_skipsUnavailableDocumentWithoutClearing() {
        KnowledgeCase target = caseOf(8L, 800L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-800", 800L)).thenReturn(
                new BidDocumentLoader.BidDocument("# 项目-800\n暂无标书正文。", null, null, false));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        assertThat(result.casesUpdated()).isZero();
        assertThat(output(target).sourceSectionTitle()).isEqualTo("因投标文件篇幅有限，如下仅展示部分对接案例。");
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_skipsWhenArchiveMissing() {
        KnowledgeCase target = caseOf(9L, 900L, TABLE_SNIPPET, null);
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-900", 900L)).thenReturn(null);

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesUpdated()).isZero();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_skipsProjectWithoutSections() {
        KnowledgeCase target = caseOf(10L, 1000L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-1000", 1000L)).thenReturn(
                new BidDocumentLoader.BidDocument("这是一段没有任何标题的正文内容。", "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        assertThat(output(target).sourceSectionTitle()).isEqualTo("因投标文件篇幅有限，如下仅展示部分对接案例。");
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_doesNotWriteBackUnnumberedSectionTitle() {
        KnowledgeCase target = caseOf(11L, 1100L, "1启明信息技术股份有限公司API大型企业", null);
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-1100", 1100L)).thenReturn(
                new BidDocumentLoader.BidDocument(UNNUMBERED_MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        // 标书可用且解析出了章节，故不是"标书不可用"的整批跳过；但命中章节无章节号 -> 定位失败
        assertThat(result.projectsFailed()).isZero();
        assertThat(result.casesUpdated()).isZero();
        assertThat(result.casesCleared()).isZero();
        assertThat(result.casesUnchanged()).isEqualTo(1);
        assertThat(output(target).sourceSectionTitle()).isNull();
        assertThat(output(target).sourceSectionPath()).isNull();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_clearsResidualProvenanceWhenTitleBlank() {
        KnowledgeCase target = caseOf(13L, 1300L, "完全不在标书中的文本", "   ");
        target.setSourceSectionPath("项目实施 > 13.2.1. 对接成功案例清单");
        target.setSourceSectionIndex(7);
        target.setSourceDocumentName("标书.pdf");
        target.setSourcePageNumber(3);
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-1300", 1300L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesCleared()).isEqualTo(1);
        assertThat(output(target).sourceSectionTitle()).isNull();
        assertThat(output(target).sourceSectionPath()).isNull();
        assertThat(output(target).sourceSectionIndex()).isNull();
        assertThat(output(target).sourceDocumentName()).isNull();
        assertThat(output(target).sourcePageNumber()).isNull();
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).id()).isEqualTo(target.getId());
    }

    @Test
    void recompute_keepsUnchangedWhenAllSourceFieldsEmpty() {
        KnowledgeCase target = caseOf(14L, 1400L, "完全不在标书中的文本", null);
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-1400", 1400L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesUnchanged()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    void recompute_clearsLegacyUnnumberedTitle() {
        KnowledgeCase target = caseOf(12L, 1200L, "完全不在标书中的文本", "项目实施");
        when(caseRepository.findActiveCandidates()).thenReturn(List.of(snapshot(target)));
        when(bidDocumentLoader.loadBidDocument("项目-1200", 1200L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesScanned()).isEqualTo(1);
        assertThat(result.casesCleared()).isEqualTo(1);
        assertThat(output(target).sourceSectionTitle()).isNull();
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).id()).isEqualTo(target.getId());
    }

    private Candidate snapshot(KnowledgeCase kase) {
        return new Candidate(kase.getId(), kase.getSourceProjectId(), kase.getResponseText(),
                kase.getScoringPointTitle(), kase.getSourceSectionTitle(), kase.getSourceSectionPath(),
                kase.getSourceSectionIndex(), kase.getSourceDocumentName(), kase.getSourcePageNumber());
    }

    private Update output(KnowledgeCase kase) {
        return writes.stream().filter(update -> update.id().equals(kase.getId())).findFirst()
                .orElse(new Update(kase.getId(), kase.getSourceSectionTitle(), kase.getSourceSectionPath(),
                        kase.getSourceSectionIndex(), kase.getSourceDocumentName(), kase.getSourcePageNumber(),
                        kase.getResponseText(), kase.getScoringPointTitle(), kase.getSourceProjectId()));
    }

}
