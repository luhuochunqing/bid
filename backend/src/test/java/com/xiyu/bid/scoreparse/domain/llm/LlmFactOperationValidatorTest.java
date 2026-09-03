// Input: deterministic ScoreFact pool + RawFactOperation / LlmDecision
// Output: LlmFactOperationValidator behavior verification (spec 059 Phase 3, T302, T304)
// Pos: Test/scoreparse/domain/llm
package com.xiyu.bid.scoreparse.domain.llm;

import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmFactOperationValidatorTest {

    private ScoreFact fact1;
    private ScoreFact fact2;
    private ScoreFact factOtherTable;

    @BeforeEach
    void setUp() {
        fact1 = new ScoreFact("fact-1", "table-0", List.of("table-0-r1-c1"), "商务资信 10分",
                "1.1", "商务部分", new BigDecimal("10"),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "10", "table-0-r1-c1", 0, 2, "10分"),
                null, null, "STRUCTURED_TABLE", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
        fact2 = new ScoreFact("fact-2", "table-0", List.of("table-0-r2-c1"), "企业业绩 5分",
                "1.2", "商务部分", new BigDecimal("5"),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "5", "table-0-r2-c1", 0, 1, "5分"),
                null, null, "STRUCTURED_TABLE", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
        factOtherTable = new ScoreFact("fact-3", "table-1", List.of("table-1-r1-c1"), "技术方案 20分",
                "2.1", "技术部分", new BigDecimal("20"),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "20", "table-1-r1-c1", 0, 2, "20分"),
                null, null, "STRUCTURED_TABLE", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    @Test
    void validateRaw_rejectsUnknownFactId() {
        RawFactOperation op = RawFactOperation.of("non-existent-fact", "KEEP", null, null, null, 1.0, List.of(), "保留");
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("unknown factId 'non-existent-fact'"));
    }

    @Test
    void validateRaw_rejectsCreateScoreFact() {
        RawFactOperation op = new RawFactOperation("new-fact-id", "CREATE_SCORE_FACT", "技术", null, null,
                1.0, List.of(), "新增一个事实", null, null, null, null);
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("illegal action 'CREATE_SCORE_FACT'"));
    }

    @Test
    void validateRaw_rejectsUpdateWeight() {
        RawFactOperation op = new RawFactOperation("fact-1", "UPDATE_WEIGHT", null, null, null,
                1.0, List.of(), "修改权重", 20.0, null, null, null);
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("illegal action 'UPDATE_WEIGHT'"));
        assertThat(result.violations()).anyMatch(v -> v.contains("protected field 'weight'"));
    }

    @Test
    void validateRaw_rejectsDeleteFact() {
        RawFactOperation op = new RawFactOperation("fact-1", "DELETE_FACT", null, null, null,
                1.0, List.of(), "删除事实", null, null, null, null);
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("illegal action 'DELETE_FACT'"));
    }

    @Test
    void validateRaw_rejectsProtectedFieldModification_detailAndSourceText() {
        RawFactOperation op = new RawFactOperation("fact-1", "KEEP", null, null, null,
                1.0, List.of(), "修改文本", null, "修改后的细则", "修改后的原文", "新事实内容");
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("protected field 'detail'"));
        assertThat(result.violations()).anyMatch(v -> v.contains("protected field 'sourceText'"));
        assertThat(result.violations()).anyMatch(v -> v.contains("illegal creation of 'newFact'"));
    }

    @Test
    void validateRaw_rejectsCrossTableParentLink() {
        RawFactOperation op = RawFactOperation.of("fact-1", "LINK_PARENT", null, "fact-3", null, 1.0, List.of(), "跨表关联");
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1, factOtherTable), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("cross-table link rejected"));
    }

    @Test
    void validateRaw_rejectsCrossTableCellReference() {
        // fact-1 来自 table-0，却引用 table-1 的 cell
        RawFactOperation op = RawFactOperation.of("fact-1", "KEEP", null, null, null, 1.0,
                List.of("table-1-r5-c2"), "跨表佐证单元格");
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("cross-table cell reference 'table-1-r5-c2'"));
    }

    @Test
    void validateRaw_flagsLowConfidenceClassification() {
        // confidence 0.65 < 0.80
        RawFactOperation op = RawFactOperation.of("fact-1", "CLASSIFY", "商务大类", null, null, 0.65, List.of(), "低置信分类");
        var result = LlmFactOperationValidator.validateRaw(List.of(fact1), List.of(op));
        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("low confidence classification 0.65"));
    }

    @Test
    void apply_validOperationsAppliedSafely() {
        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.Classify("fact-1", "评分大类", 0.95),
                new LlmFactOp.LinkParent("fact-2", "fact-1"),
                new LlmFactOp.MarkDuplicate("fact-1", List.of("fact-2"))
        ));
        List<ScoreFact> pool = List.of(fact1, fact2);
        List<ScoreFact> applied = LlmFactOperationValidator.apply(pool, decision);

        assertThat(applied).hasSize(2);
        ScoreFact applied1 = applied.stream().filter(f -> f.factId().equals("fact-1")).findFirst().orElseThrow();
        ScoreFact applied2 = applied.stream().filter(f -> f.factId().equals("fact-2")).findFirst().orElseThrow();

        assertThat(applied1.dimension()).isEqualTo("评分大类");
        assertThat(applied2.parentFactId()).isEqualTo("fact-1");
        assertThat(applied2.validationStatus()).isEqualTo(ScoreFact.ValidationStatus.DUPLICATE);
    }

    @Test
    void apply_onViolations_doesNotMutateFacts_marksReviewRequired() {
        // 违规操作：跨表链接
        LlmDecision decision = new LlmDecision(List.of(
                new LlmFactOp.LinkParent("fact-1", "fact-3")
        ));
        List<ScoreFact> pool = List.of(fact1, factOtherTable);
        List<ScoreFact> applied = LlmFactOperationValidator.apply(pool, decision);

        ScoreFact applied1 = applied.stream().filter(f -> f.factId().equals("fact-1")).findFirst().orElseThrow();
        // 原事实未被挂上非法 parentFactId，但状态被标记为 REVIEW_REQUIRED
        assertThat(applied1.parentFactId()).isNullOrEmpty();
        assertThat(applied1.validationStatus()).isEqualTo(ScoreFact.ValidationStatus.REVIEW_REQUIRED);
    }
}
