package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.ExtractedScoreItem;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand;
import com.xiyu.bid.scoreparse.entity.ScoreExtractionRecord;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreActiveVersionRepository;
import com.xiyu.bid.scoreparse.repository.ScoreExtractionRecordRepository;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreVersionActivationServiceTest {

    private static final Long PROJECT_ID = 1394L;
    private static final Long PARSE_ID = 99L;
    private static final String TASK_ID = "task-1394";

    @Mock private ScoreParseTaskRepository tasks;
    @Mock private ScoreExtractionRecordRepository snapshots;
    @Mock private ScoreActiveVersionRepository versions;
    @Mock private ScoreItemRepository items;
    @Mock private ScoreResultRepository results;
    @Mock private ProjectAccessScopeService accessScopeService;

    private ScoreExtractionCodec codec;
    private ScoreVersionActivationService service;

    @BeforeEach
    void setUp() {
        codec = new ScoreExtractionCodec(new ObjectMapper());
        service = new ScoreVersionActivationService(
                tasks, snapshots, versions, items, results, codec,
                new ScoreExtractionVersionFeature(true), accessScopeService);
        lenient().when(tasks.lockProjectForTaskChange(PROJECT_ID)).thenReturn(PROJECT_ID);
        lenient().when(items.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of());
        lenient().when(versions.findFirstByProjectIdOrderByIdDesc(PROJECT_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("版本化手动激活与自动激活均拦截空名称/纯序号的历史冻结快照项")
    void rejectsEmptyOrInvalidNameInFrozenSnapshotOnBothAutoAndManualActivation() {
        ScoreFact legacyFact = new ScoreFact("f-1", "t1", List.of("c1"), "规则原文得10分", "1", "",
                BigDecimal.TEN, null, null, "", "MODEL", .9, ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM);
        OriginalScoreEvidencePolicy.Qualification q = new OriginalScoreEvidencePolicy.Qualification(
                "f-1", "scheme-main", "IN_SCHEME", true, null, "t1:c1");
        ExtractedScoreItem legacyEmptyNameItem = new ExtractedScoreItem(
                1, "item-empty", "f-1", "scheme-main", "file-1", "", "", "规则原文得10分",
                List.of(), BigDecimal.TEN, null, BigDecimal.TEN, "POINTS", "OBJECTIVE",
                null, "SUM", Map.of(), List.of(), "AUTO_APPROVED", BigDecimal.ONE, true, legacyFact, q);
        ScoreExtractionSnapshot frozenLegacy = new ScoreExtractionSnapshot(
                1,
                List.of(legacyEmptyNameItem),
                List.of(new ScoreExtractionSnapshot.Scheme("scheme-main", "评分方案", 1, BigDecimal.TEN, true, 1, BigDecimal.TEN, BigDecimal.TEN, "KNOWN")),
                List.of());

        stubFrozenTask(frozenLegacy);

        assertThat(service.tryAutoActivate(PROJECT_ID, TASK_ID)).isFalse();
        assertThatThrownBy(() -> service.activate(
                PROJECT_ID, TASK_ID,
                new ActivateScoreExtractionCommand("scheme-main", "empty:v1", "人工激活", List.of("item-empty")),
                "admin"))
                .isInstanceOf(ScoreExtractionException.class)
                .hasMessage("INVALID_OR_NONCOMPUTABLE_SELECTION");
        verify(items, never()).saveAllAndFlush(any());
    }

    @Test
    void autoActivatesComputableRulesWhenAnUnnamedSummaryIsNotAnEffectiveUnit() {
        var facts = List.of(
                new ScoreFact("summary", "t1", List.of("c1"), "", "", "", BigDecimal.TEN,
                        null, null, "", "MODEL", .9, ScoreFact.ValidationStatus.REVIEW_REQUIRED, ChildAggregation.SUM),
                new ScoreFact("rule", "t1", List.of("c2"), "提供证书得10分", "1", "人员资质",
                        BigDecimal.TEN, null, null, "summary", "MODEL", .9,
                        ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM));
        var qualification = new OriginalScoreEvidencePolicy.Qualification(
                "rule", "scheme-main", "IN_SCHEME", true, null, "t1:c2");
        var named = new ExtractedScoreItem(1, "item-rule", "rule", "scheme-main", "file-1",
                "人员资质", "", "提供证书得10分", List.of(), BigDecimal.TEN, null, BigDecimal.TEN,
                "POINTS", "OBJECTIVE", null, "SUM", Map.of(), List.of(), "AUTO_APPROVED",
                BigDecimal.ONE, true, facts.get(1), qualification);
        var summary = new ExtractedScoreItem(1, "item-summary", "summary", "scheme-main", "file-1",
                "", "", "", List.of(), BigDecimal.TEN, null, null, "POINTS", "", null, "SUM", Map.of(),
                List.of("NOT_EFFECTIVE_SCORING_UNIT", "INVALID_SCORE_ITEM_NAME"), "REVIEW_REQUIRED", BigDecimal.ONE, false,
                facts.getFirst(), new OriginalScoreEvidencePolicy.Qualification(
                        "summary", "scheme-main", "IN_SCHEME", true, null, "t1:c1"));
        stubFrozenTask(new ScoreExtractionSnapshot(1, List.of(named, summary),
                List.of(new ScoreExtractionSnapshot.Scheme("scheme-main", "评分方案", 2, BigDecimal.TEN,
                        true, 1, BigDecimal.TEN, BigDecimal.TEN, "KNOWN")), List.of()));
        lenient().when(versions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.tryAutoActivate(PROJECT_ID, TASK_ID)).isTrue();
        verify(items).saveAllAndFlush(argThat((List<ScoreItem> saved) ->
                saved.size() == 1 && "人员资质".equals(saved.getFirst().getDim())));
    }

    private void stubFrozenTask(ScoreExtractionSnapshot snapshot) {
        ScoreParseTask task = ScoreParseTask.builder()
                .id(PARSE_ID).projectId(PROJECT_ID).taskId(TASK_ID)
                .taskType("PARSE").status("COMPLETED").timeoutMarked(false).build();
        ScoreExtractionRecord record = ScoreExtractionRecord.builder()
                .parseTaskId(PARSE_ID).projectId(PROJECT_ID)
                .baseFingerprint("empty:v1")
                .payload(codec.encode(snapshot))
                .frozenAt(LocalDateTime.now())
                .build();

        when(tasks.findByTaskId(TASK_ID)).thenReturn(Optional.of(task));
        when(tasks.findLatestParseTaskForUpdate(PROJECT_ID)).thenReturn(Optional.of(task));
        when(snapshots.findById(PARSE_ID)).thenReturn(Optional.of(record));

    }
}
