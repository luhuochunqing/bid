package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.ExtractedScoreItem;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
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

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "评分项 21（30分）", "1（30分）", "30分", "商务"})
    @DisplayName("版本化手动激活与自动激活均拦截名称无效的历史冻结快照项")
    void rejectsEmptyOrInvalidNameInFrozenSnapshotOnBothAutoAndManualActivation(String name) {
        ScoreFact legacyFact = new ScoreFact("f-1", "t1", List.of("c1"), "规则原文得10分", "1", "",
                BigDecimal.TEN, null, null, "", "MODEL", .9, ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM);
        OriginalScoreEvidencePolicy.Qualification q = new OriginalScoreEvidencePolicy.Qualification(
                "f-1", "scheme-main", "IN_SCHEME", true, null, "t1:c1");
        ExtractedScoreItem legacyItem = new ExtractedScoreItem(
                1, "item-legacy", "f-1", "scheme-main", "file-1", name, "", "规则原文得10分",
                List.of(), BigDecimal.TEN, null, BigDecimal.TEN, "POINTS", "OBJECTIVE",
                null, "SUM", Map.of(), List.of(), "AUTO_APPROVED", BigDecimal.ONE, true, legacyFact, q);
        ScoreExtractionSnapshot frozenLegacy = new ScoreExtractionSnapshot(
                1,
                List.of(legacyItem),
                List.of(new ScoreExtractionSnapshot.Scheme("scheme-main", "评分方案", 1, BigDecimal.TEN, true, 1, BigDecimal.TEN, BigDecimal.TEN, "KNOWN")),
                List.of());

        var task = stubFrozenTask(frozenLegacy);
        when(tasks.findLatestParseTaskForUpdate(PROJECT_ID)).thenReturn(Optional.of(task));
        lenient().when(versions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertAll(
                () -> assertThat(service.tryAutoActivate(PROJECT_ID, TASK_ID)).isFalse(),
                () -> assertThatThrownBy(() -> service.activate(PROJECT_ID, TASK_ID,
                        new ActivateScoreExtractionCommand("scheme-main", "empty:v1", "人工激活", List.of("item-legacy")),
                        "admin")).isInstanceOf(ScoreExtractionException.class)
                        .hasMessage("INVALID_OR_NONCOMPUTABLE_SELECTION"));
        verify(versions, never()).saveAndFlush(any());
        verify(items, never()).deleteByProjectId(PROJECT_ID);
        verify(items, never()).saveAllAndFlush(any());
    }

    @Test
    void mixedHistoricalSnapshotRequiresExplicitSelectionBeforeReplacingExistingRules() {
        var snapshotItems = List.of(historicalItem("item-invalid", ""), historicalItem("item-valid", "人员资质（10分）"));
        stubFrozenTask(new ScoreExtractionSnapshot(1, snapshotItems,
                List.of(new ScoreExtractionSnapshot.Scheme("scheme-main", "评分方案", 2, BigDecimal.valueOf(20),
                        true, 2, BigDecimal.valueOf(20), BigDecimal.valueOf(20), "KNOWN")), List.of()));
        var oldItem = ScoreItem.builder().id(123L).dim("现行评分规则").weight(BigDecimal.valueOf(20)).build();
        when(items.findByProjectIdOrderByItemIndexAsc(PROJECT_ID)).thenReturn(List.of(oldItem));
        when(versions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var fingerprint = service.currentToken(PROJECT_ID).fingerprint();

        assertThatThrownBy(() -> service.activate(PROJECT_ID, TASK_ID,
                new ActivateScoreExtractionCommand("scheme-main", fingerprint, "核对历史快照", null), "admin"))
                .isInstanceOf(ScoreExtractionException.class)
                .hasMessage("INVALID_OR_NONCOMPUTABLE_SELECTION");
        verify(versions, never()).saveAndFlush(any());
        verify(results, never()).deleteByScoreItemIdIn(any());
        verify(items, never()).deleteByProjectId(PROJECT_ID);

        service.activate(PROJECT_ID, TASK_ID,
                new ActivateScoreExtractionCommand("scheme-main", fingerprint, "选用已核对项目", List.of("item-valid")), "admin");
        verify(results).deleteByScoreItemIdIn(List.of(oldItem.getId()));
        verify(items).saveAllAndFlush(argThat((List<ScoreItem> saved) ->
                saved.size() == 1 && "人员资质（10分）".equals(saved.getFirst().getDim())));
    }

    private static ExtractedScoreItem historicalItem(String id, String name) {
        var fact = new ScoreFact(id, "t1", List.of(id), "提供证书得10分", "1", name,
                BigDecimal.TEN, null, null, "", "MODEL", .9, ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM);
        var qualification = new OriginalScoreEvidencePolicy.Qualification(id, "scheme-main", "IN_SCHEME", true, null, id);
        return new ExtractedScoreItem(1, id, id, "scheme-main", "file-1", name, "", fact.sourceText(),
                List.of(), BigDecimal.TEN, null, BigDecimal.TEN, "POINTS", "OBJECTIVE", null, "SUM",
                Map.of(), List.of(), "AUTO_APPROVED", BigDecimal.ONE, true, fact, qualification);
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
        var task = stubFrozenTask(new ScoreExtractionSnapshot(1, List.of(named, summary),
                List.of(new ScoreExtractionSnapshot.Scheme("scheme-main", "评分方案", 2, BigDecimal.TEN,
                        true, 1, BigDecimal.TEN, BigDecimal.TEN, "KNOWN")), List.of()));
        when(tasks.findLatestParseTaskForUpdate(PROJECT_ID)).thenReturn(Optional.of(task));
        lenient().when(versions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.tryAutoActivate(PROJECT_ID, TASK_ID)).isTrue();
        verify(items).saveAllAndFlush(argThat((List<ScoreItem> saved) ->
                saved.size() == 1 && "人员资质".equals(saved.getFirst().getDim())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void recoversVersionedTitleAcrossAPointsCellOnlyWhenTheOriginalContainsIt(boolean hasSourceTitle) {
        String rule = "投标人提供营业收入证明得30分";
        String section = "第三章 评审办法";
        var table = new CanonicalTable("t", null, section, 1, 3, List.of(),
                List.of(revenueCell("title", 0, hasSourceTitle ? "营业总收入" : "商务"), revenueCell("points", 1, "30分"),
                        revenueCell("rule", 2, rule)), true, "DOCX");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "hash", "DOCX", "v"),
                List.of(table), List.of());
        var metadata = new ScoreSourceMetadata("营业总收入：" + rule, rule, List.of(), List.of());
        var model = new MultimodalScoreFact(section, "1", "", "商务", BigDecimal.valueOf(30), rule,
                "OBJECTIVE", 1, "t", List.of("rule"), rule, .9, ChildAggregation.SUM, "", "", metadata);
        var validation = new MultimodalScoreFactValidator().validate(List.of(model), artifact, BigDecimal.valueOf(30));
        var fact = TextPrimaryScoreFactMapper.map(model, artifact, validation);

        var scope = new OriginalScoreEvidencePolicy.Qualification(fact.factId(),
                "scheme-main", "IN_SCHEME", true, "OK", "t:rule");
        var item = ScoreExtractionSnapshot.from(TASK_ID, List.of(fact), List.of(scope), List.of()).items().getFirst();

        assertThat(fact.dimension()).isEqualTo(hasSourceTitle ? "营业总收入" : "商务");
        assertThat(item.computable()).isEqualTo(hasSourceTitle);
    }

    private static CanonicalCellDto revenueCell(String id, int column, String text) {
        String path = "/w:document/w:body/w:tbl[1]/w:tr[1]/w:tc[" + (column + 1) + "]";
        return new CanonicalCellDto(id, 0, column, text, text, 1, 1, false, path, null);
    }

    @Test
    void activatesQuantifiedRevenueAsObjectiveDespiteTheModelGuess() {
        var metadata = new com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata("", "", List.of(), List.of(), "", "SUBJECTIVE");
        var fact = new ScoreFact("f-revenue", "t1", List.of("c1"), "2024年营业总收入大于10亿元得10分",
                "1", "营业总收入", BigDecimal.TEN, null, null, "", "MODEL", .9,
                ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM, metadata);
        var scope = new OriginalScoreEvidencePolicy.Qualification(
                fact.factId(), "scheme-main", "IN_SCHEME", true, null, "t1:c1");
        var snapshot = ScoreExtractionSnapshot.from(TASK_ID, List.of(fact), List.of(scope), List.of());
        stubFrozenTask(snapshot);
        when(versions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.activate(PROJECT_ID, TASK_ID,
                new ActivateScoreExtractionCommand("scheme-main", "empty:v1", "核对营业收入",
                        List.of(snapshot.items().getFirst().itemId())), "admin");

        verify(items).saveAllAndFlush(argThat((List<ScoreItem> saved) ->
                saved.size() == 1 && "OBJECTIVE".equals(saved.getFirst().getScoreType())));
    }

    private ScoreParseTask stubFrozenTask(ScoreExtractionSnapshot snapshot) {
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
        when(snapshots.findById(PARSE_ID)).thenReturn(Optional.of(record));
        return task;
    }
}
