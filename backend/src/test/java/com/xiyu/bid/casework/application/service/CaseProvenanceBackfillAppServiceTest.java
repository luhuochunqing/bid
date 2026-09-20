package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.BidDocumentLoader;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * CaseProvenanceBackfillAppService 单元测试：存量案例来源章节重算。
 *
 * <p>契约：只处理 source_section_title 不是已验证章节（空 / 句子型伪标题 / 无编号小标题）的 ACTIVE 案例；
 * 按项目分组，同一项目标书只装载一次；定位成功写回真实章节，定位失败且五个来源字段中<b>任一非空</b>时
 * 整体清空（前端随即显示「暂无来源标题」），五个字段全空则不写库。标书不可用（无归档、占位文本、
 * 未解析出章节）时整批跳过并计入 projectsFailed，不改写任何字段。
 */
class CaseProvenanceBackfillAppServiceTest {

    private static final String MARKDOWN = """
            # 13.2. 对接平台对接成功案例及证明材料

            ## 13.2.1. 对接成功案例清单
            序号客户名称企业类型1启明信息技术股份有限公司API大型企业
            """;

    private static final String TABLE_SNIPPET = "序号客户名称企业类型1启明信息技术股份有限公司API大型企业";

    /** 目标章节只有无编号小标题的标书：层级树仍解析出章节，但该章节不满足"章节号 + 标题文字"契约。 */
    private static final String UNNUMBERED_MARKDOWN = """
            # 13.2. 对接平台对接成功案例及证明材料

            ## 项目实施
            1启明信息技术股份有限公司API大型企业
            """;

    private KnowledgeCaseRepository caseRepository;
    private ProjectRepository projectRepository;
    private BidDocumentLoader bidDocumentLoader;
    private CaseProvenanceBackfillAppService service;

    @BeforeEach
    void setUp() {
        caseRepository = mock(KnowledgeCaseRepository.class);
        projectRepository = mock(ProjectRepository.class);
        bidDocumentLoader = mock(BidDocumentLoader.class);
        service = new CaseProvenanceBackfillAppService(caseRepository, projectRepository, bidDocumentLoader);
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
    @DisplayName("空标题案例：定位成功写入真实章节（标题/路径/序号/页码/文档名）")
    void recompute_writesProvenanceForNullTitleCase() {
        KnowledgeCase target = caseOf(1L, 100L, TABLE_SNIPPET, null);
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-100", 100L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "商务技术标书.pdf", 12, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesScanned()).isEqualTo(1);
        assertThat(result.casesUpdated()).isEqualTo(1);
        assertThat(target.getSourceSectionTitle()).isEqualTo("13.2.1. 对接成功案例清单");
        assertThat(target.getSourceSectionPath())
                .isEqualTo("13.2. 对接平台对接成功案例及证明材料 > 13.2.1. 对接成功案例清单");
        assertThat(target.getSourceSectionIndex()).isEqualTo(2);
        assertThat(target.getSourceDocumentName()).isEqualTo("商务技术标书.pdf");
        assertThat(target.getSourcePageNumber()).isEqualTo(12);
        verify(caseRepository).saveAll(List.of(target));
    }

    @Test
    @DisplayName("句子型伪标题 + 定位失败：清空伪标题，前端随即显示「暂无来源标题」")
    void recompute_clearsSentenceLikeTitleWhenLocateFails() {
        KnowledgeCase target = caseOf(2L, 200L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-200", 200L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesCleared()).isEqualTo(1);
        assertThat(target.getSourceSectionTitle()).isNull();
        assertThat(target.getSourceSectionPath()).isNull();
        assertThat(target.getSourceSectionIndex()).isNull();
        verify(caseRepository).saveAll(List.of(target));
    }

    @Test
    @DisplayName("句子型伪标题 + 定位成功：覆盖为真实章节")
    void recompute_replacesSentenceLikeTitle() {
        KnowledgeCase target = caseOf(3L, 300L, TABLE_SNIPPET, "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-300", 300L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesUpdated()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        assertThat(target.getSourceSectionTitle()).isEqualTo("13.2.1. 对接成功案例清单");
    }

    @Test
    @DisplayName("标书装载失败：计入 projectsFailed，不写库、不抛异常")
    void recompute_countsFailedProject() {
        KnowledgeCase target = caseOf(4L, 400L, TABLE_SNIPPET, null);
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument(anyString(), anyLong()))
                .thenThrow(new IllegalStateException("OBS 签名失败，无法下载标书"));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesUpdated()).isZero();
        assertThat(result.casesCleared()).isZero();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("同项目多案例：标书只装载一次")
    void recompute_loadsBidDocumentOncePerProject() {
        KnowledgeCase first = caseOf(5L, 500L, TABLE_SNIPPET, null);
        KnowledgeCase second = caseOf(6L, 500L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(first, second));
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
    @DisplayName("已有合法标题的案例不参与重算")
    void recompute_skipsCasesWithValidTitle() {
        KnowledgeCase valid = caseOf(7L, 700L, TABLE_SNIPPET, "13.2.1. 对接成功案例清单");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(valid));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesScanned()).isZero();
        verifyNoInteractions(bidDocumentLoader);
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("标书不可用（占位文本）：整批跳过，不清空存量来源字段")
    void recompute_skipsUnavailableDocumentWithoutClearing() {
        KnowledgeCase target = caseOf(8L, 800L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-800", 800L)).thenReturn(
                new BidDocumentLoader.BidDocument("# 项目-800\n暂无标书正文。", null, null, false));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        assertThat(result.casesUpdated()).isZero();
        assertThat(target.getSourceSectionTitle()).isEqualTo("因投标文件篇幅有限，如下仅展示部分对接案例。");
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("项目无归档（装载返回 null）：整批跳过，不清空存量来源字段")
    void recompute_skipsWhenArchiveMissing() {
        KnowledgeCase target = caseOf(9L, 900L, TABLE_SNIPPET, null);
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-900", 900L)).thenReturn(null);

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesUpdated()).isZero();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("标书可用但一个章节都没解析出来：无法证明来源，跳过而非清空")
    void recompute_skipsProjectWithoutSections() {
        KnowledgeCase target = caseOf(10L, 1000L, "完全不在标书中的文本", "因投标文件篇幅有限，如下仅展示部分对接案例。");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-1000", 1000L)).thenReturn(
                new BidDocumentLoader.BidDocument("这是一段没有任何标题的正文内容。", "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.projectsFailed()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        assertThat(target.getSourceSectionTitle()).isEqualTo("因投标文件篇幅有限，如下仅展示部分对接案例。");
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("命中无编号小标题：不写回该标题，来源保持为空（前端显示「暂无来源标题」）")
    void recompute_doesNotWriteBackUnnumberedSectionTitle() {
        KnowledgeCase target = caseOf(11L, 1100L, "1启明信息技术股份有限公司API大型企业", null);
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-1100", 1100L)).thenReturn(
                new BidDocumentLoader.BidDocument(UNNUMBERED_MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        // 标书可用且解析出了章节，故不是"标书不可用"的整批跳过；但命中章节无章节号 -> 定位失败
        assertThat(result.projectsFailed()).isZero();
        assertThat(result.casesUpdated()).isZero();
        assertThat(result.casesCleared()).isZero();
        assertThat(result.casesUnchanged()).isEqualTo(1);
        assertThat(target.getSourceSectionTitle()).isNull();
        assertThat(target.getSourceSectionPath()).isNull();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("标题已空但路径/页码残留：定位失败时一并清空（不留部分写入的来源元数据）")
    void recompute_clearsResidualProvenanceWhenTitleBlank() {
        KnowledgeCase target = caseOf(13L, 1300L, "完全不在标书中的文本", "   ");
        target.setSourceSectionPath("项目实施 > 13.2.1. 对接成功案例清单");
        target.setSourceSectionIndex(7);
        target.setSourceDocumentName("标书.pdf");
        target.setSourcePageNumber(3);
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-1300", 1300L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesCleared()).isEqualTo(1);
        assertThat(target.getSourceSectionTitle()).isNull();
        assertThat(target.getSourceSectionPath()).isNull();
        assertThat(target.getSourceSectionIndex()).isNull();
        assertThat(target.getSourceDocumentName()).isNull();
        assertThat(target.getSourcePageNumber()).isNull();
        verify(caseRepository).saveAll(List.of(target));
    }

    @Test
    @DisplayName("五个来源字段全空且定位失败：无需写库，计为未变")
    void recompute_keepsUnchangedWhenAllSourceFieldsEmpty() {
        KnowledgeCase target = caseOf(14L, 1400L, "完全不在标书中的文本", null);
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-1400", 1400L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesUnchanged()).isEqualTo(1);
        assertThat(result.casesCleared()).isZero();
        verify(caseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("存量无编号标题（非句子型）同样进入重算：定位失败即清空")
    void recompute_clearsLegacyUnnumberedTitle() {
        KnowledgeCase target = caseOf(12L, 1200L, "完全不在标书中的文本", "项目实施");
        when(caseRepository.findByStatus("ACTIVE")).thenReturn(List.of(target));
        when(bidDocumentLoader.loadBidDocument("项目-1200", 1200L))
                .thenReturn(new BidDocumentLoader.BidDocument(MARKDOWN, "标书.pdf", null, true));

        CaseProvenanceBackfillAppService.BackfillResult result = service.recomputeAll();

        assertThat(result.casesScanned()).isEqualTo(1);
        assertThat(result.casesCleared()).isEqualTo(1);
        assertThat(target.getSourceSectionTitle()).isNull();
        verify(caseRepository).saveAll(List.of(target));
    }
}
