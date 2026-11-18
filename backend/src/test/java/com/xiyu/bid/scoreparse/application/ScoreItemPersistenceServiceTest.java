// Input: projectId/parseTaskId/candidates（合并去重后候选）
// Output: ScoreItemPersistenceService 行为验证（FR-021 覆盖语义 + code fallback + 分类落库）
// Pos: scoreparse/application 单元测试（spec 041 US1/US5）
// 维护声明: 维护者按项目SOP；FR-021 细节自 ScoreParseAppServiceTest 迁入
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FR-021 重新解析覆盖语义测试（spec 041 / data-model.md §score_item）。
 * <p>重新解析 MUST 覆盖解析结果：新批次写入前按 project_id 清理旧
 * score_item，且旧 score_result 随旧 item ID 显式删除（无 FK 级联）。
 */
@ExtendWith(MockitoExtension.class)
class ScoreItemPersistenceServiceTest {

    private static final Long PROJECT_ID = 42L;
    private static final Long PARSE_TASK_ID = 5L;

    @Mock
    private ScoreItemRepository itemRepository;
    @Mock
    private ScoreResultRepository resultRepository;
    @Mock
    private ScoreParseFactRepository factRepository;
    @Mock
    private ScoreParseEvidenceRepository evidenceRepository;

    private ScoreItemPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ScoreItemPersistenceService(itemRepository, resultRepository, factRepository, evidenceRepository);
    }

    @Test
    void reparse_deletesStaleResultsAndItemsBeforeInsert() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of(oldItem(100L), oldItem(101L)));

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate("A1", "具备 CMMI 5 级认证证书", "60"),
                candidate("A2", "近三年类似项目业绩不少于 3 个", "40")));

        // FR-021：旧打分结果随旧评分项失效清理，再删旧评分项
        verify(resultRepository).deleteByScoreItemIdIn(List.of(100L, 101L));
        verify(itemRepository).deleteByProjectId(PROJECT_ID);

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue().get(0).getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getValue().get(0).getParseTaskId()).isEqualTo(PARSE_TASK_ID);
    }

    @Test
    void firstParse_noStaleData_skipsDeletion() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate("A1", "具备 CMMI 5 级认证证书", "60")));

        verify(resultRepository, never()).deleteByScoreItemIdIn(anyList());
        verify(itemRepository, never()).deleteByProjectId(PROJECT_ID);
        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void blankCode_savedAsEmptyString_noFakeIndex() {
        // spec 046 FR-004：空 code 落空串，不再补假编号（原 String.valueOf(i+1) 已移除）
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate(null, "具备 ISO9001 认证", "30"),
                candidate("  ", "项目负责人具备 PMP", "20")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue().get(0).getCode()).isEmpty();
        assertThat(saved.getValue().get(0).getItemIndex()).isEqualTo(1);
        assertThat(saved.getValue().get(1).getCode()).isEmpty();
        assertThat(saved.getValue().get(1).getItemIndex()).isEqualTo(2);
    }

    @Test
    void outOfOrderCodes_itemIndexFollowsNumericCodeOrder() {
        // spec 046 FR-003：乱序候选（6,7,1）落库 itemIndex 按编号数值序重编
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                candidate("6", "技术方案", "30"),
                candidate("7", "实施方案", "20"),
                candidate("1", "资质业绩", "50")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue()).extracting(ScoreItem::getCode)
                .containsExactly("1", "6", "7");
        assertThat(saved.getValue()).extracting(ScoreItem::getItemIndex)
                .containsExactly(1, 2, 3);
    }

    @Test
    void scoreType_classifiedByPolicy_notLlmGuess() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                // guess 为 null 时按 detail 规则兜底 → OBJECTIVE
                new ScoreCandidate("A1", "资质", "具备 CMMI 5 级认证证书",
                        new BigDecimal("60"), null, null,
                        "具备 CMMI 5 级认证证书", "P47", "SEMANTIC"),
                // AI 结构化判定优先 → OBJECTIVE
                new ScoreCandidate("A2", "技术", "方案阐述清晰",
                        new BigDecimal("20"), "OBJECTIVE", null,
                        "方案阐述清晰", "P48", "SEMANTIC"),
                // 描述性规则优先于模型猜测 → SUBJECTIVE（即使猜测为 OBJECTIVE）
                new ScoreCandidate("A3", "技术", "技术方案先进合理，专家酌情给分",
                        new BigDecimal("20"), "OBJECTIVE", null,
                        "技术方案先进合理，专家酌情给分", "P49", "SEMANTIC")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue().get(0).getScoreType()).isEqualTo("OBJECTIVE");
        assertThat(saved.getValue().get(1).getScoreType()).isEqualTo("OBJECTIVE");
        assertThat(saved.getValue().get(2).getScoreType()).isEqualTo("SUBJECTIVE");
        assertThat(saved.getValue().get(0).getStatusStage1()).isEqualTo("PENDING");
    }

    @Test
    void nullOrBlankDim_fallsBackToDetailOrPlaceholder_neverNull() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                new ScoreCandidate("1", null, "技术实施方案完整得10分",
                        new BigDecimal("10"), "OBJECTIVE", null, "原文", "P1", null),
                new ScoreCandidate("2", "   ", "商务资质证书得20分",
                        new BigDecimal("20"), "OBJECTIVE", null, "原文", "P2", null),
                new ScoreCandidate("3", null, null,
                        new BigDecimal("30"), "OBJECTIVE", null, "原文", "P3", null)));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        List<ScoreItem> items = saved.getValue();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getDim()).isEqualTo("技术实施方案完整得10分");
        assertThat(items.get(1).getDim()).isEqualTo("商务资质证书得20分");
        assertThat(items.get(2).getDim()).isEqualTo("评分项");
    }

    @Test
    void oversizedDimAndCode_truncatedToColumnLimits() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        String longCode = "C".repeat(70);
        String longDim = "D".repeat(250);
        String longLocation = "L".repeat(300);
        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                new ScoreCandidate(longCode, longDim, "详细要素",
                        new BigDecimal("10"), "OBJECTIVE", null, "原文", longLocation, null)));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        ScoreItem item = saved.getValue().get(0);
        assertThat(item.getCode()).hasSize(50);
        assertThat(item.getDim()).hasSize(200);
        assertThat(item.getLocation()).hasSize(200);
    }

    @Test
    void categoryDim_withExtractableTitle_resolvesToSpecificItemName() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        String detail = "能够提供最长\n账期\n最长账期6个月（含）以上的，得5分；最长账期3个月(含)~6个月的，得3分；其余不得分。";
        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                new ScoreCandidate("longest_payment_period", "商务", detail,
                        new BigDecimal("5"), "OBJECTIVE", null, detail, "P1", "TABLE_GRID")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        assertThat(saved.getValue().get(0).getDim()).isEqualTo("能够提供最长账期");
    }

    @Test
    void parentOnlyDim_extractsTitleFromDetailWhenAvailable() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                new ScoreCandidate("1", "商务部分", "1. 人员配置：具备相关高级工程师证书得10分。",
                        new BigDecimal("10"), "OBJECTIVE", null, "原文", "P1", "TABLE_GRID",
                        "商务部分", "PARENT_SINGLE"),
                new ScoreCandidate("2", "技术部分", "技术部分",
                        new BigDecimal("20"), "OBJECTIVE", null, "原文", "P2", "TABLE_GRID",
                        "技术部分", "UNKNOWN")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        List<ScoreItem> items = saved.getValue();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getDim()).isEqualTo("人员配置");
        assertThat(items.get(1).getDim()).isEqualTo("技术部分");
    }

    @Test
    void parentOnlyDim_withNullDetail_doesNotThrow() {
        when(itemRepository.findByProjectIdOrderByItemIndexAsc(PROJECT_ID))
                .thenReturn(List.of());

        service.persistItems(PROJECT_ID, PARSE_TASK_ID, List.of(
                new ScoreCandidate("1", "商务部分", null,
                        new BigDecimal("10"), "OBJECTIVE", null, "原文", "P1", "TABLE_GRID",
                        "商务部分", "PARENT_SINGLE")));

        ArgumentCaptor<List<ScoreItem>> saved = capturedSavedItems();
        List<ScoreItem> items = saved.getValue();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getDim()).isEqualTo("商务部分");
    }

    @Test
    void reviewRequiredCandidates_arePersistedAsReviewFactsWithoutOverwritingScoreItems() {
        List<ScoreCandidate> candidates = List.of(
                new ScoreCandidate("2", "公司综合实力", "具备 ISO9001 认证得6分",
                        new BigDecimal("6"), "OBJECTIVE", null,
                        "投标人具有有效期内的认证证书，每有一项加1分，最高得6分。", "P12", "LEGACY_REVIEW_CANDIDATE"));

        service.persistReviewCandidateFacts(PROJECT_ID, PARSE_TASK_ID, candidates);

        verify(itemRepository, never()).deleteByProjectId(PROJECT_ID);
        verify(itemRepository, never()).saveAll(anyList());
        verify(factRepository).deleteByParseTaskId(PARSE_TASK_ID);
        verify(evidenceRepository).deleteByParseTaskId(PARSE_TASK_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoreParseFact>> savedFacts = ArgumentCaptor.forClass(List.class);
        verify(factRepository).saveAll(savedFacts.capture());
        assertThat(savedFacts.getValue()).hasSize(1);
        ScoreParseFact fact = savedFacts.getValue().get(0);
        assertThat(fact.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(fact.getParseTaskId()).isEqualTo(PARSE_TASK_ID);
        assertThat(fact.getCode()).isEqualTo("2");
        assertThat(fact.getDimension()).isEqualTo("公司综合实力");
        assertThat(fact.getWeight()).isEqualByComparingTo("6");
        assertThat(fact.getValidationStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(fact.getExtractionMethod()).isEqualTo("LEGACY_REVIEW_CANDIDATE");
        assertThat(fact.getSourceText()).contains("认证证书");
    }

    @Test
    void persistFactsAndEvidence_keepsOutOfSchemeFactWithTypedQualificationAudit() throws Exception {
        ScoreFact inScheme = new ScoreFact("in-scheme", "t-score", List.of("score-rule"),
                "技术方案最高得100分", "1", "技术方案", new BigDecimal("100"), null, null,
                null, "TABLE_ROW", 0.95, ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        ScoreFact outOfScheme = new ScoreFact("contract", "t-contract", List.of("contract-rule"),
                "合同履约考核最高得40分", "2", "履约考核", new BigDecimal("40"), null, null,
                null, "TABLE_ROW", 0.95, ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        List<OriginalScoreEvidencePolicy.Qualification> qualifications = List.of(
                new OriginalScoreEvidencePolicy.Qualification("in-scheme", "scheme-main", "IN_SCHEME", true,
                        null, "t-score:score-rule"),
                new OriginalScoreEvidencePolicy.Qualification("contract", "scheme-contract", "OUT_OF_SCHEME", false,
                        "ITEM_SCHEME_SCOPE_MISMATCH", "t-contract:contract-rule"));

        service.persistFactsAndEvidence(PROJECT_ID, PARSE_TASK_ID, List.of(inScheme, outOfScheme), qualifications);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.xiyu.bid.scoreparse.entity.ScoreParseFact>> facts = ArgumentCaptor.forClass(List.class);
        verify(factRepository).saveAll(facts.capture());
        assertThat(facts.getValue()).extracting(com.xiyu.bid.scoreparse.entity.ScoreParseFact::getFactId)
                .containsExactly("in-scheme", "contract");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.xiyu.bid.scoreparse.entity.ScoreParseEvidence>> evidence =
                ArgumentCaptor.forClass(List.class);
        verify(evidenceRepository).saveAll(evidence.capture());
        assertThat(evidence.getValue()).hasSize(2);
        var audit = evidence.getValue().stream().filter(row -> "contract".equals(row.getFactId())).findFirst().orElseThrow();
        assertThat(audit.getKind()).isEqualTo("SCHEME_QUALIFICATION");
        var qualification = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(audit.getExplanation());
        assertThat(qualification.path("scopeStatus").asText()).isEqualTo("OUT_OF_SCHEME");
        assertThat(qualification.path("candidateEligible").asBoolean()).isFalse();
        assertThat(qualification.path("reason").asText()).isEqualTo("ITEM_SCHEME_SCOPE_MISMATCH");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<ScoreItem>> capturedSavedItems() {
        ArgumentCaptor<List<ScoreItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        return captor;
    }

    private ScoreItem oldItem(Long id) {
        return ScoreItem.builder()
                .id(id).projectId(PROJECT_ID).itemIndex(1)
                .code("OLD").dim("旧项").detail("旧解析结果")
                .weight(new BigDecimal("10")).scoreType("OBJECTIVE")
                .statusStage1("OK").build();
    }

    private ScoreCandidate candidate(String code, String detail, String weight) {
        return new ScoreCandidate(code, "资质", detail, new BigDecimal(weight),
                "OBJECTIVE", null, detail, "P47", "SEMANTIC");
    }
}
