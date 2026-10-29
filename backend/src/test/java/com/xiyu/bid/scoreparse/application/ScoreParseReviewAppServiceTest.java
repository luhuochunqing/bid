package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseReviewRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreParseReviewAppServiceTest {

    private static final Long PROJECT_ID = 100L;

    @Mock
    private ScoreParseTaskRepository taskRepository;
    @Mock
    private ScoreParseFactRepository factRepository;
    @Mock
    private ScoreParseEvidenceRepository evidenceRepository;
    @Mock
    private ScoreParseReviewRepository reviewRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private ScoreItemPersistenceService itemPersistenceService;
    @Mock
    private EstimatedScoreService estimatedScoreService;

    private ScoreParseReviewAppService service;

    @BeforeEach
    void setUp() {
        service = new ScoreParseReviewAppService(taskRepository, factRepository, evidenceRepository,
                reviewRepository, projectAccessScopeService, itemPersistenceService, estimatedScoreService);
    }

    @Test
    void getReviewSnapshot_returnsFactsEvidenceAndAuditsForLatestParseTask() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "10")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(evidence("fact-1")));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of(review("fact-1")));

        ScoreParseReviewDTO dto = service.getReviewSnapshot(PROJECT_ID);

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(PROJECT_ID);
        assertThat(dto.decisionStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(dto.facts()).hasSize(1);
        assertThat(dto.facts().get(0).evidences()).hasSize(1);
        assertThat(dto.audits()).hasSize(1);
    }

    @Test
    void getReviewSnapshotReturnsPersistedChildAggregationAndLegacyDefaultsToSum() {
        mockLatestTask();
        ScoreParseFact sharedCap = fact("fact-1", "10");
        sharedCap.setChildAggregation("SHARED_CAP");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(sharedCap), List.of(fact("fact-1", "10")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        ScoreParseReviewDTO dto = service.getReviewSnapshot(PROJECT_ID);
        ScoreParseReviewDTO legacyDto = service.getReviewSnapshot(PROJECT_ID);

        assertThat(dto.facts().get(0).childAggregation()).isEqualTo("SHARED_CAP");
        assertThat(legacyDto.facts().get(0).childAggregation()).isEqualTo("SUM");
    }

    @Test
    void getReviewSnapshotKeepsDifferentSchemesSeparateAndRequiresAnExplicitChoice() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("scheme-a-fact", "100"),
                fact("scheme-b-fact", "150")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                schemeQualification("scheme-a-fact", "scheme-a", "IN_SCHEME", true, null,
                        "第一评分方案", "100", "VERIFIED", "text:line[1]", "MULTI_TABLE_SAME_SCHEME", List.of("a1", "a2")),
                schemeQualification("scheme-b-fact", "scheme-b", "IN_SCHEME", true, null,
                        "第二评分方案", "150", "VERIFIED", "text:line[5]", "SINGLE_TABLE", List.of("b1"))));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        ScoreParseReviewDTO dto = service.getReviewSnapshot(PROJECT_ID);

        assertThat(dto.schemeSelectionRequired()).isTrue();
        assertThat(dto.selectedSchemeId()).isNull();
        assertThat(dto.declaredTotal()).isNull();
        assertThat(dto.schemes()).extracting(ScoreParseReviewDTO.Scheme::schemeId)
                .containsExactly("scheme-a", "scheme-b");
        assertThat(dto.schemes()).extracting(ScoreParseReviewDTO.Scheme::declaredTotal)
                .containsExactly(new BigDecimal("100"), new BigDecimal("150"));
        assertThat(dto.schemes().get(0).tableIds()).containsExactly("a1", "a2");
    }

    @Test
    void submitReview_savesAuditOnlyAndReturnsSnapshot() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "10")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("fact-1", "MANUAL_ADJUST", new BigDecimal("12"), "人工确认权重")
        ), "复核"), "zhangsan");

        ArgumentCaptor<ScoreParseReview> captor = ArgumentCaptor.forClass(ScoreParseReview.class);
        verify(reviewRepository).save(captor.capture());
        ScoreParseReview saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getParseTaskId()).isEqualTo(9L);
        assertThat(saved.getFactId()).isEqualTo("fact-1");
        assertThat(saved.getAction()).isEqualTo("MANUAL_ADJUST");
        assertThat(saved.getOriginalWeight()).isEqualByComparingTo("10");
        assertThat(saved.getAdjustedWeight()).isEqualByComparingTo("12");
        assertThat(saved.getOperatorName()).isEqualTo("zhangsan");
    }

    @Test
    void submitReview_adoptCandidates_persistsItemsAndMarksManualApproved() {
        mockLatestTask();
        ScoreParseFact fact1 = fact("fact-1", "20");
        ScoreParseFact fact2 = fact("fact-2", "80");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact1, fact2));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidence("fact-1", "fact-2"));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        ScoreParseReviewDTO result = service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "一键采纳所有候选项")
        ), "人工确认采纳", 9L), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        var ordered = inOrder(taskRepository, itemPersistenceService);
        ordered.verify(taskRepository).lockProjectForTaskChange(PROJECT_ID);
        ordered.verify(taskRepository).findLatestParseTaskForUpdate(PROJECT_ID);
        ordered.verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        verify(estimatedScoreService).estimateForProject(PROJECT_ID);

        ArgumentCaptor<ScoreParseTask> taskCaptor = ArgumentCaptor.forClass(ScoreParseTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        ScoreParseTask updatedTask = taskCaptor.getValue();
        assertThat(updatedTask.getDecisionStatus()).isEqualTo("MANUAL_APPROVED");
        assertThat(updatedTask.getOriginalTotalWeight()).isEqualByComparingTo("100");
        assertThat(updatedTask.getFallbackReason()).isNull();

        ArgumentCaptor<ScoreParseReview> reviewCaptor = ArgumentCaptor.forClass(ScoreParseReview.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getAction()).isEqualTo("ADOPT_CANDIDATES");
    }

    @Test
    void submitReview_adoptCandidates_excludesPersistedOutOfSchemeFactsFromDefaultSelection() {
        mockLatestTask();
        ScoreParseFact inScheme = fact("in-scheme", "100");
        ScoreParseFact outOfScheme = fact("contract-duty", "40");
        outOfScheme.setDimension("履约考核");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(inScheme, outOfScheme));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                qualification("in-scheme", "scheme-main", "IN_SCHEME", true, null),
                qualification("contract-duty", "contract-performance", "OUT_OF_SCHEME", false,
                        "ITEM_SCHEME_SCOPE_MISMATCH")));

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "默认采纳本评分方案候选")
        ), "人工确认采纳", 9L), "admin");

        ArgumentCaptor<List<com.xiyu.bid.scoreparse.domain.ScoreCandidate>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), captor.capture());
        assertThat(captor.getValue()).hasSize(1).extracting(com.xiyu.bid.scoreparse.domain.ScoreCandidate::dim)
                .containsExactly("资质");
    }

    @Test
    void submitReview_adoptCandidates_allowsExplicitInclusionOfReviewedFacts() {
        mockLatestTask();
        ScoreParseFact outOfScheme = fact("contract-duty", "100");
        outOfScheme.setDimension("履约考核");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(outOfScheme));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                qualification("contract-duty", "contract-performance", "OUT_OF_SCHEME", false,
                        "ITEM_SCHEME_SCOPE_MISMATCH")));

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("contract-duty", "ADOPT_CANDIDATES", new BigDecimal("100"), "人工确认纳入")
        ), "人工确认采纳", 9L), "admin");

        ArgumentCaptor<List<com.xiyu.bid.scoreparse.domain.ScoreCandidate>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), captor.capture());
        assertThat(captor.getValue()).hasSize(1).extracting(com.xiyu.bid.scoreparse.domain.ScoreCandidate::dim)
                .containsExactly("履约考核");
    }

    @Test
    void submitReview_adoptCandidates_rejectsNonExistentFact() {
        mockLatestTask();
        ScoreParseFact inScheme = fact("in-scheme", "100");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(inScheme));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                qualification("in-scheme", "scheme-main", "IN_SCHEME", true, null)));

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("ghost-fact-999", "ADOPT_CANDIDATES", new BigDecimal("100"), "未知事实")
        ), "人工确认采纳", 9L), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("包含不存在的评分事实");

        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_adoptCandidates_closesAgainstDeclaredEightyPoints() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        task.setRunSummary("{\"tableSplits\":[{\"declaredTotal\":80}]} ");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "30"), fact("fact-2", "50")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidenceTotal("80", "fact-1", "fact-2"));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "采纳80分制候选项")
        ), "人工确认采纳", 9L), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("80");
    }

    @Test
    void submitReview_adoptCandidates_allowsPartialWeightAndPersistsActualTotal() {
        mockLatestTask();
        ScoreParseFact fact1 = fact("fact-1", "30");
        ScoreParseFact fact2 = fact("fact-2", "61");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact1, fact2));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidenceTotal("100", "fact-1", "fact-2"));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "人工确认采纳91分")
        ), "人工确认采纳", 9L), "admin");

        ArgumentCaptor<List<com.xiyu.bid.scoreparse.domain.ScoreCandidate>> candidates = ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), candidates.capture());
        assertThat(candidates.getValue()).hasSize(2);
        ArgumentCaptor<ScoreParseTask> taskCaptor = ArgumentCaptor.forClass(ScoreParseTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getOriginalTotalWeight()).isEqualByComparingTo("91");
    }

    @Test
    void submitReview_allowsExplicitSourceUnverifiedFactWithoutScheme() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("unverified", "100")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of());
        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("unverified", "ADOPT_CANDIDATES", null, "人工确认")
        ), "人工确认采纳", 9L), "admin");
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_unverifiedFactFollowsExplicitSelectedScheme() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("a", "10"), fact("b", "20"), fact("u", "5")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                qualification("a", "scheme-a", "IN_SCHEME", true, null),
                qualification("b", "scheme-b", "IN_SCHEME", true, null)));
        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("u", "ADOPT_CANDIDATES", null, "人工确认")
        ), "人工确认采纳", 9L, "scheme-a"), "admin");
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_rejectsSelectedFactFromAnotherScheme() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("a", "10"), fact("b", "20")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                qualification("a", "scheme-a", "IN_SCHEME", true, null),
                qualification("b", "scheme-b", "IN_SCHEME", true, null)));
        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("b", "ADOPT_CANDIDATES", null, "人工确认")
        ), "人工确认采纳", 9L, "scheme-a"), "admin")).isInstanceOf(IllegalArgumentException.class);
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_adoptCandidates_acceptsWeightAboveDeclaredTotalAfterManualReview() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "101")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidenceTotal("100", "fact-1"));

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "人工确认")
        ), "人工确认采纳", 9L), "admin");
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_adoptCandidates_rejectsNegativeAdjustedWeightBeforePersistence() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "100"), fact("fact-2", "9")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidenceTotal("100", "fact-1", "fact-2"));

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("fact-1", "ADOPT_CANDIDATES", new BigDecimal("100"), "人工确认"),
                new ScoreParseReviewCommand.Action("fact-2", "ADOPT_CANDIDATES", new BigDecimal("-9"), "人工确认")
        ), "人工确认采纳", 9L), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为负数");

        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_adoptsMutuallyExclusiveTiersUsingParentMaximumOnce() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        ScoreParseFact parent = fact("tier-parent", "5");
        parent.setChildAggregation("MUTUALLY_EXCLUSIVE");
        ScoreParseFact tierFive = childFact("tier-five", "5", "tier-parent");
        ScoreParseFact tierFour = childFact("tier-four", "4", "tier-parent");
        ScoreParseFact tierThree = childFact("tier-three", "3", "tier-parent");
        ScoreParseFact tierTwo = childFact("tier-two", "2", "tier-parent");
        List<ScoreParseFact> facts = List.of(parent, tierFive, tierFour, tierThree, tierTwo);
        when(factRepository.findByParseTaskId(9L)).thenReturn(facts);
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidenceTotal(
                "5", "tier-parent", "tier-five", "tier-four", "tier-three", "tier-two"));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "采纳互斥分档")
        ), "人工确认采纳", 9L), "admin");

        ArgumentCaptor<List<com.xiyu.bid.scoreparse.domain.ScoreCandidate>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(candidate -> {
            assertThat(candidate.dim()).isEqualTo("资质");
            assertThat(candidate.weight()).isEqualByComparingTo("5");
        });
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("5");
    }

    @Test
    void submitReview_adoptsCumulativeChildrenAsTheirSum() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        ScoreParseFact parent = fact("sum-parent", "20");
        parent.setChildAggregation("SUM");
        List<ScoreParseFact> facts = List.of(parent,
                childFact("sum-five-a", "5", "sum-parent"),
                childFact("sum-five-b", "5", "sum-parent"),
                childFact("sum-ten", "10", "sum-parent"));
        when(factRepository.findByParseTaskId(9L)).thenReturn(facts);
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(eligibleEvidenceTotal(
                "20", "sum-parent", "sum-five-a", "sum-five-b", "sum-ten"));
        when(reviewRepository.findByParseTaskId(9L)).thenReturn(List.of());

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "采纳累计子项")
        ), "人工确认采纳", 9L), "admin");

        ArgumentCaptor<List<com.xiyu.bid.scoreparse.domain.ScoreCandidate>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), captor.capture());
        assertThat(captor.getValue()).hasSize(3)
                .extracting(com.xiyu.bid.scoreparse.domain.ScoreCandidate::weight)
                .containsExactly(new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("10"));
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("20");
    }

    @Test
    void submitReview_adoptCandidates_usesConfirmedCandidateTotalForSingleUnknownTable() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        task.setRunSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\"}]}");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "40"), fact("fact-2", "60")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(unknownEvidence("fact-1", "fact-2"));

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "确认单表候选合计")
        ), "人工确认采纳", 9L), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("100");
    }

    @Test
    void submitReview_adoptCandidates_usesConfirmedCandidateTotalForMultiTableUnknownTotal() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        task.setRunSummary("{\"tableSplits\":[{\"tableId\":\"table-1\"},{\"tableId\":\"table-2\"}]}");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "30"), fact("fact-2", "39")));
        List<ScoreParseEvidence> multiTableEvidence = List.of(
                schemeQualification("fact-1", "scheme-main", "IN_SCHEME", true, null, "综合评估法", null, "UNKNOWN", "", "MULTI_TABLE", List.of("table-1", "table-2")),
                schemeQualification("fact-2", "scheme-main", "IN_SCHEME", true, null, "综合评估法", null, "UNKNOWN", "", "MULTI_TABLE", List.of("table-1", "table-2"))
        );
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(multiTableEvidence);

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "确认多表候选合计69分")
        ), "人工确认采纳", 9L), "admin");

        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
        assertThat(task.getOriginalTotalWeight()).isEqualByComparingTo("69");
        assertThat(task.getDecisionStatus()).isEqualTo("MANUAL_APPROVED");
    }

    @Test
    void submitReview_adoptCandidates_acceptsConflictingDeclaredTotalsAfterManualReview() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                PROJECT_ID, "PARSE", List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED")).get(0);
        task.setRunSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\",\"declaredTotal\":60},"
                + "{\"tableId\":\"business\",\"declaredTotal\":40}]}");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("fact-1", "60"), fact("fact-2", "40")));
        when(evidenceRepository.findByParseTaskId(9L)).thenReturn(List.of(
                schemeQualification("fact-1", "scheme-main", "IN_SCHEME", true, null, "本项目评分办法",
                        null, "CONFLICT", "", "MULTI_TABLE_SAME_SCHEME", List.of("business", "technical")),
                schemeQualification("fact-2", "scheme-main", "IN_SCHEME", true, null, "本项目评分办法",
                        null, "CONFLICT", "", "MULTI_TABLE_SAME_SCHEME", List.of("business", "technical"))));

        service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "人工确认")
        ), "人工确认采纳", 9L), "admin");
        verify(itemPersistenceService).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_rejectsFailedLatestParseBeforePublishingOldCandidates() {
        ScoreParseTask failed = ScoreParseTask.builder().id(10L).taskId("new-failed")
                .projectId(PROJECT_ID).taskType("PARSE").status("FAILED")
                .decisionStatus("REVIEW_REQUIRED").build();
        when(taskRepository.findLatestParseTaskForUpdate(PROJECT_ID)).thenReturn(java.util.Optional.of(failed));
        when(factRepository.findByParseTaskId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("fact-from-old-task", "ADOPT_CANDIDATES", new BigDecimal("100"), "旧页面")
        ), "采纳"), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("解析失败");
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(10L), anyList());
    }

    @Test
    void submitReview_rejectsOldTaskIdEvenWhenNewParseCompleted() {
        mockLatestTask();
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of(fact("new-fact", "100")));

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action("old-fact", "ADOPT_CANDIDATES", new BigDecimal("100"), "旧页面")
        ), "采纳", 8L), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务已变化");
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void submitReview_rejectsRepeatedAdoptionWithoutRewritingItems() {
        mockLatestTask();
        ScoreParseTask task = taskRepository.findLatestParseTaskForUpdate(PROJECT_ID).orElseThrow();
        task.setDecisionStatus("MANUAL_APPROVED");
        when(factRepository.findByParseTaskId(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitReview(PROJECT_ID, new ScoreParseReviewCommand(List.of(
                new ScoreParseReviewCommand.Action(null, "ADOPT_CANDIDATES", null, "重复点击")
        ), "重复", 9L), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已采纳");
        verify(itemPersistenceService, never()).persistItems(eq(PROJECT_ID), eq(9L), anyList());
    }

    @Test
    void declaredTotalDoesNotAddPhysicalTableTotalsOrGuessTheSchemeTotal() {
        ScoreParseTask explicit = ScoreParseTask.builder().taskId("explicit")
                .runSummary("{\"tableSplits\":[{\"tableId\":\"a\",\"declaredTotal\":60},{\"tableId\":\"b\",\"declaredTotal\":40}]}")
                .build();
        ScoreParseTask unknown = ScoreParseTask.builder().taskId("unknown")
                .runSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\",\"declaredTotal\":60},{\"tableId\":\"b\",\"declaredTotal\":40}]}")
                .build();

        assertThat(ScoreParseReviewAppService.declaredTotal(explicit)).isNull();
        assertThat(ScoreParseReviewAppService.declaredTotal(unknown)).isNull();
    }

    @Test
    void declaredTotal_usesTenderTextTotalForSingleUnknownTableGroup() {
        ScoreParseTask task = ScoreParseTask.builder().taskId("text-total")
                .runSummary("{\"tableSplits\":[{\"tableId\":\"NO_DECLARED_TOTAL\",\"declaredTotal\":80}]}")
                .build();
        assertThat(ScoreParseReviewAppService.declaredTotal(task)).isEqualByComparingTo("80");
    }

    private void mockLatestTask() {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(9L)
                .taskId("parse-task")
                .projectId(PROJECT_ID)
                .taskType("PARSE")
                .status("COMPLETED")
                .decisionStatus("REVIEW_REQUIRED")
                .fallbackReason("WEIGHT_SUM_MISMATCH")
                .reviewReason("权重不匹配")
                .pipelineVersion("EVIDENCE_PIPELINE_V1")
                .artifactSha256("sha256")
                .build();
        lenient().when(taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                eq(PROJECT_ID), eq("PARSE"), eq(List.of("PENDING", "PROCESSING", "COMPLETED", "FAILED"))))
                .thenReturn(List.of(task));
        lenient().when(taskRepository.findLatestParseTaskForUpdate(PROJECT_ID))
                .thenReturn(java.util.Optional.of(task));
    }

    private ScoreParseFact fact(String factId, String weight) {
        return ScoreParseFact.builder()
                .id(1L)
                .projectId(PROJECT_ID)
                .parseTaskId(9L)
                .factId(factId)
                .code("A1")
                .dimension("资质")
                .weight(new BigDecimal(weight))
                .sourceText("具备 CMMI 认证")
                .extractionMethod("SIDE_CAR")
                .validationStatus("REVIEW_REQUIRED")
                .confidence(BigDecimal.ONE)
                .build();
    }

    private ScoreParseFact childFact(String factId, String weight, String parentFactId) {
        ScoreParseFact fact = fact(factId, weight);
        fact.setParentFactId(parentFactId);
        return fact;
    }

    private ScoreParseEvidence evidence(String factId) {
        return ScoreParseEvidence.builder()
                .id(3L)
                .parseTaskId(9L)
                .factId(factId)
                .kind("SOURCE_CELL")
                .cellId("c1")
                .quotedText("CMMI 认证")
                .build();
    }

    private ScoreParseEvidence qualification(String factId, String schemeId, String scopeStatus,
                                             boolean candidateEligible, String reason) {
        return schemeQualification(factId, schemeId, scopeStatus, candidateEligible, reason,
                "scheme-main".equals(schemeId) ? "本项目评分办法" : schemeId,
                "100", "VERIFIED", "text:line[0]", "SINGLE_TABLE", List.of("score-table"));
    }

    private List<ScoreParseEvidence> eligibleEvidence(String... factIds) {
        return java.util.Arrays.stream(factIds)
                .map(factId -> qualification(factId, "scheme-main", "IN_SCHEME", true, null)).toList();
    }

    private List<ScoreParseEvidence> eligibleEvidenceTotal(String total, String... factIds) {
        return java.util.Arrays.stream(factIds).map(factId -> schemeQualification(factId, "scheme-main", "IN_SCHEME",
                true, null, "本项目评分办法", total, "VERIFIED", "text:line[0]", "SINGLE_TABLE",
                List.of("score-table"))).toList();
    }

    private List<ScoreParseEvidence> unknownEvidence(String... factIds) {
        return java.util.Arrays.stream(factIds).map(factId -> schemeQualification(factId, "scheme-main", "IN_SCHEME",
                true, null, "本项目评分办法", null, "UNKNOWN", "", "SINGLE_TABLE",
                List.of("score-table"))).toList();
    }

    private ScoreParseEvidence schemeQualification(String factId, String schemeId, String scopeStatus,
                                                   boolean candidateEligible, String reason, String title,
                                                   String declaredTotal, String totalStatus, String totalLocator,
                                                   String layoutStatus, List<String> tableIds) {
        String numeric = declaredTotal == null ? "null" : declaredTotal;
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        String tableIdsJson = tableIds.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String explanation = "{\"schemeId\":\"" + schemeId + "\",\"schemeTitle\":\"" + title
                + "\",\"scopeStatus\":\"" + scopeStatus + "\",\"candidateEligible\":" + candidateEligible
                + ",\"reason\":" + reasonJson + ",\"declaredTotal\":" + numeric
                + ",\"totalEvidenceStatus\":\"" + totalStatus + "\",\"totalLocator\":\"" + totalLocator
                + "\",\"totalQuote\":\"总分100分\",\"layoutStatus\":\"" + layoutStatus
                + "\",\"tableIds\":" + tableIdsJson + "}";
        return ScoreParseEvidence.builder().id(20L).parseTaskId(9L).factId(factId)
                .kind("SCHEME_QUALIFICATION").explanation(explanation).build();
    }

    private ScoreParseReview review(String factId) {
        return ScoreParseReview.builder()
                .projectId(PROJECT_ID)
                .parseTaskId(9L)
                .factId(factId)
                .action("RETAIN_ORIGINAL")
                .reason("保留现有项")
                .operatorName("zhangsan")
                .build();
    }
}
