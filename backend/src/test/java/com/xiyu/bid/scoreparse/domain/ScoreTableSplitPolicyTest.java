// Input: 多张评分表的分组事实 + 全表汇总声明 + 正文
// Output: 每张独立评分表的完整性结果 + 文档级聚合
// Pos: scoreparse/domain — 纯核心单测（spec 062 Red 骨架，编译前定义 ScoreTableSplitPolicy 签名）
package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableSplitPolicyTest {

    private ScoreFact fact(String factId, String tableId, String dim, String weight) {
        String quoted = "得" + weight + "分";
        ScoreEvidence ev = new ScoreEvidence(
                ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, quoted, tableId + "-c1", 0, quoted.length(), "明确分值");
        return new ScoreFact(
                factId, tableId, List.of(tableId + "-c1"), "item " + factId, "1.1", dim,
                new BigDecimal(weight), ev, null, "",
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    private ScoreTableSummaryPolicy.DeclaredTotalEvidence total(String tableId, String value) {
        return new ScoreTableSummaryPolicy.DeclaredTotalEvidence(
                new BigDecimal(value), tableId, 99, tableId + "-total", "满分（" + value + "分）");
    }

    @Test
    void multiTable_eachOwnTotalSelfConsistent_allAutoApproved() {
        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(
                        fact("t1-f1", "t1", "技术方案", "30"),
                        fact("t1-f2", "t1", "技术团队", "20"),
                        fact("t1-f3", "t1", "技术培训", "10"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(
                        fact("t2-f1", "t2", "商务报价", "20"),
                        fact("t2-f2", "t2", "售后服务", "20"))));

        TableSplitResult result = ScoreTableSplitPolicy.split(
                tables, List.of(total("t1", "60"), total("t2", "40")), null);

        assertThat(result.tables()).hasSize(2);
        assertThat(result.tables()).allMatch(TableCompleteness::hasOwnDeclaredTotal);
        assertThat(result.tables())
                .filteredOn(t -> "t1".equals(t.tableId()))
                .singleElement()
                .extracting(t -> t.completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.tables())
                .filteredOn(t -> "t2".equals(t.tableId()))
                .singleElement()
                .extracting(t -> t.completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.documentLevel().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
    }

    @Test
    void multiTable_oneTableInconsistent_documentLevelReviewButConsistentTableAutoApproved() {
        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(
                        fact("t1-f1", "t1", "技术方案", "30"),
                        fact("t1-f2", "t1", "技术团队", "20"),
                        fact("t1-f3", "t1", "技术培训", "10"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(
                        fact("t2-f1", "t2", "商务报价", "20"),
                        fact("t2-f2", "t2", "售后服务", "10"))));

        TableSplitResult result = ScoreTableSplitPolicy.split(
                tables, List.of(total("t1", "60"), total("t2", "40")), null);

        assertThat(result.tables())
                .filteredOn(t -> "t1".equals(t.tableId()))
                .singleElement()
                .extracting(t -> t.completeness().decision())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.tables())
                .filteredOn(t -> "t2".equals(t.tableId()))
                .singleElement()
                .extracting(t -> t.completeness().decision())
                .isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.documentLevel().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
    }

    @Test
    void noTableWithOwnDeclaredTotal_mergedGroupMatchesCurrentBehavior() {
        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(
                        fact("t1-f1", "t1", "技术方案", "30"),
                        fact("t1-f2", "t1", "技术团队", "20"),
                        fact("t1-f3", "t1", "技术培训", "10"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(
                        fact("t2-f1", "t2", "商务报价", "20"),
                        fact("t2-f2", "t2", "售后服务", "20"))));

        TableSplitResult result = ScoreTableSplitPolicy.split(tables, List.of(), null);

        // 现状行为：无独立总分证据 → 合并为单组走默认 100 兜底；5 个叶子合计 100 → AUTO_APPROVED
        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables()).singleElement().satisfies(t -> {
            assertThat(t.tableId()).isEqualTo("NO_DECLARED_TOTAL");
            assertThat(t.hasOwnDeclaredTotal()).isFalse();
            assertThat(t.sourceTableIds()).containsExactly("t1", "t2");
            assertThat(t.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        });
        assertThat(result.documentLevel().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
    }

    @Test
    void singleTableSelfConsistent_resultMatchesDirectPolicyCall() {
        List<ScoreFact> facts = List.of(
                fact("t1-f1", "t1", "技术方案", "30"),
                fact("t1-f2", "t1", "技术团队", "20"),
                fact("t1-f3", "t1", "技术培训", "50"));
        List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> totals = List.of(total("t1", "100"));
        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, facts));

        TableSplitResult result = ScoreTableSplitPolicy.split(tables, totals, null);

        ScoreCompletenessPolicy.ScoreCompletenessResult direct = ScoreCompletenessPolicy.evaluateCompleteness(
                facts, null, true, totals);

        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables()).singleElement().satisfies(t -> {
            assertThat(t.tableId()).isEqualTo("t1");
            assertThat(t.hasOwnDeclaredTotal()).isTrue();
            assertThat(t.completeness()).isEqualTo(direct);
        });
        assertThat(result.documentLevel()).isEqualTo(direct);
    }
}
