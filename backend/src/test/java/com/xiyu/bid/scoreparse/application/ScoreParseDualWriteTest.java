package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseReviewRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5 双写与版本化元数据契约测试（spec 059 Phase 5）。
 */
class ScoreParseDualWriteTest {

    private ScoreItemRepository itemRepository;
    private ScoreResultRepository resultRepository;
    private ScoreParseFactRepository factRepository;
    private ScoreParseEvidenceRepository evidenceRepository;
    private ScoreParseReviewRepository reviewRepository;
    private ScoreItemPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ScoreItemRepository.class);
        resultRepository = mock(ScoreResultRepository.class);
        factRepository = mock(ScoreParseFactRepository.class);
        evidenceRepository = mock(ScoreParseEvidenceRepository.class);
        reviewRepository = mock(ScoreParseReviewRepository.class);
        persistenceService = new ScoreItemPersistenceService(
                itemRepository, resultRepository, factRepository, evidenceRepository);
    }

    @Test
    @DisplayName("双写：保存事实与单元格证据并验证不可变事实快照写入")
    void persistFactsAndEvidence_savesFactsAndEvidenceEntities() {
        Long projectId = 1001L;
        Long parseTaskId = 2001L;

        ScoreEvidence ev = new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "方案完整性 10分", "c-1", 0, 10, "单元格提取");
        ScoreFact fact = new ScoreFact("fact-1", "tbl-1", List.of("c-1"), "方案完整性 10分",
                "1.1", "技术", new BigDecimal("10.00"), ev, null, null,
                "TABLE_ROW", 0.95, ScoreFact.ValidationStatus.AUTO_APPROVED);

        persistenceService.persistFactsAndEvidence(projectId, parseTaskId, List.of(fact));

        verify(factRepository).deleteByProjectId(projectId);
        verify(evidenceRepository).deleteByParseTaskId(parseTaskId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreParseFact>> factCaptor = ArgumentCaptor.forClass(List.class);
        verify(factRepository).saveAll(factCaptor.capture());
        List<ScoreParseFact> savedFacts = factCaptor.getValue();
        assertThat(savedFacts).hasSize(1);
        ScoreParseFact savedFact = savedFacts.get(0);
        assertThat(savedFact.getFactId()).isEqualTo("fact-1");
        assertThat(savedFact.getProjectId()).isEqualTo(projectId);
        assertThat(savedFact.getParseTaskId()).isEqualTo(parseTaskId);
        assertThat(savedFact.getWeight()).isEqualByComparingTo("10.00");
        assertThat(savedFact.getSourceCellIds()).contains("c-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreParseEvidence>> evCaptor = ArgumentCaptor.forClass(List.class);
        verify(evidenceRepository).saveAll(evCaptor.capture());
        List<ScoreParseEvidence> savedEvs = evCaptor.getValue();
        assertThat(savedEvs).hasSize(1);
        ScoreParseEvidence savedEv = savedEvs.get(0);
        assertThat(savedEv.getFactId()).isEqualTo("fact-1");
        assertThat(savedEv.getCellId()).isEqualTo("c-1");
        assertThat(savedEv.getKind()).isEqualTo("EXPLICIT_WEIGHT_COLUMN");
    }

    @Test
    @DisplayName("双写兼容：score_item 写入时包含 extractionMethod 与 confidence 等证据字段")
    void persistItems_populatesEvidenceFieldsOnScoreItem() {
        Long projectId = 1001L;
        Long parseTaskId = 2001L;

        ScoreCandidate candidate = new ScoreCandidate(
                "1.1", "技术方案", "方案完整性", new BigDecimal("10.00"),
                "OBJECTIVE", "无", "原文依据", "page-1", "EVIDENCE_PIPELINE");

        when(itemRepository.findByProjectIdOrderByItemIndexAsc(projectId)).thenReturn(List.of());
        when(itemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        persistenceService.persistItems(projectId, parseTaskId, List.of(candidate));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(itemCaptor.capture());
        List<ScoreItem> saved = itemCaptor.getValue();
        assertThat(saved).hasSize(1);
        ScoreItem item = saved.get(0);
        assertThat(item.getExtractionMethod()).isEqualTo("page-1");
        assertThat(item.getConfidence()).isEqualTo(BigDecimal.ONE);
        assertThat(item.getValidationStatus()).isEqualTo("AUTO_APPROVED");
    }

    @Test
    @DisplayName("Meta 构造：包含 artifactSha256 与 reviewReason 版本化元数据")
    void metaBuilder_includesArtifactSha256AndReviewReason() {
        InitiationTenderTextResolver tenderResolver = mock(InitiationTenderTextResolver.class);
        when(tenderResolver.findLatestTenderDocument(any())).thenReturn(Optional.empty());
        var snapshotRepo = mock(com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository.class);
        when(snapshotRepo.findTopByProjectIdOrderByCreatedAtDescIdDesc(any())).thenReturn(Optional.empty());
        var taskRepo = mock(ScoreParseTaskRepository.class);
        var itemRepo = mock(ScoreItemRepository.class);
        var resRepo = mock(ScoreResultRepository.class);
        when(itemRepo.findByProjectIdOrderByItemIndexAsc(any())).thenReturn(List.of());

        var docRepo = mock(com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository.class);
        when(docRepo.findByProjectIdAndFiltersOrderByCreatedAtDesc(any(), any(), any(), any())).thenReturn(List.of());
        ScoreParseItemsMetaBuilder builder = new ScoreParseItemsMetaBuilder(
                tenderResolver, snapshotRepo, taskRepo, itemRepo, resRepo,
                new ScoreBidDocumentLookup(docRepo, null, null, null, null));

        ScoreParseTask task = ScoreParseTask.builder()
                .id(1L)
                .projectId(1001L)
                .taskId("t-1")
                .status("COMPLETED")
                .pipelineVersion("EVIDENCE_PIPELINE_V1")
                .decisionStatus("REVIEW_REQUIRED")
                .fallbackReason("WEIGHT_SUM_MISMATCH")
                .artifactSha256("sha256-abcdef1234567890")
                .reviewReason("权重合计80分不符合100分预期")
                .build();

        ScoreParseItemsDTO.Meta meta = builder.build(1001L, task, null);

        assertThat(meta.pipelineVersion()).isEqualTo("EVIDENCE_PIPELINE_V1");
        assertThat(meta.decisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(meta.fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
        assertThat(meta.artifactSha256()).isEqualTo("sha256-abcdef1234567890");
        assertThat(meta.reviewReason()).isEqualTo("权重合计80分不符合100分预期");
    }

    @Test
    @DisplayName("复核实体：ScoreParseReview 能够记录人工调整审计")
    void scoreParseReview_recordsAuditCorrectly() {
        ScoreParseReview review = ScoreParseReview.builder()
                .projectId(1001L)
                .parseTaskId(2001L)
                .factId("fact-1")
                .action("MANUAL_ADJUST")
                .originalWeight(new BigDecimal("10.00"))
                .adjustedWeight(new BigDecimal("15.00"))
                .reason("修正招标文件子项漏算5分")
                .operatorId(99L)
                .operatorName("审核员张三")
                .build();

        assertThat(review.getAction()).isEqualTo("MANUAL_ADJUST");
        assertThat(review.getOriginalWeight()).isEqualByComparingTo("10.00");
        assertThat(review.getAdjustedWeight()).isEqualByComparingTo("15.00");
        assertThat(review.getReason()).contains("修正招标文件");
        assertThat(review.getOperatorName()).isEqualTo("审核员张三");
    }
}
