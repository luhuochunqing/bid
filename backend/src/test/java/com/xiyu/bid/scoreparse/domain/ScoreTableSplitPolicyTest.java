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
    void multiTableWithoutOwnDeclaredTotal_staysUnknownAndRequiresReview() {
        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(
                        fact("t1-f1", "t1", "技术方案", "30"),
                        fact("t1-f2", "t1", "技术团队", "20"),
                        fact("t1-f3", "t1", "技术培训", "10"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(
                        fact("t2-f1", "t2", "商务报价", "20"),
                        fact("t2-f2", "t2", "售后服务", "20"))));

        TableSplitResult result = ScoreTableSplitPolicy.split(tables, List.of(), null);

        assertThat(result.tables()).hasSize(2);
        assertThat(result.tables()).allSatisfy(t -> {
            assertThat(t.tableId()).isIn("t1", "t2");
            assertThat(t.hasOwnDeclaredTotal()).isFalse();
            assertThat(t.sourceTableIds()).containsExactly(t.tableId());
            assertThat(t.completeness().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
            assertThat(t.completeness().declaredTotalScore()).isNull();
            assertThat(t.completeness().declaredTotalSource())
                    .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN);
        });
        assertThat(result.documentLevel().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
    }

    @Test
    void multiTableDocumentTotalClosesAcrossPhysicalTables() {
        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(
                        fact("t1-f1", "t1", "商务", "30"),
                        fact("t1-f2", "t1", "业绩", "40"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(
                        fact("t2-f1", "t2", "技术", "30"))));

        TableSplitResult result = ScoreTableSplitPolicy.split(tables, List.of(),
                "本项目总分为 100 分，由商务部分、技术部分组成。");

        assertThat(result.tables()).singleElement().satisfies(table -> {
            assertThat(table.tableId()).isEqualTo("DOCUMENT");
            assertThat(table.completeness().decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        });
        assertThat(result.documentLevel().leafWeightSum()).isEqualByComparingTo("100");
    }

    @Test
    void multiTablePartialMaximumMustNotProveDocumentTotal() {
        var tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(fact("a", "t1", "商务", "30"), fact("b", "t1", "业绩", "40"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(fact("c", "t2", "技术", "30"))));
        var result = ScoreTableSplitPolicy.split(tables, List.of(), "本项目评分部分满分为100分。");
        assertThat(result.documentLevel().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.tables()).noneMatch(t -> "DOCUMENT".equals(t.tableId()));
    }

    @Test
    void multiTablePartialTotalMustNotProveDocumentTotal() {
        var tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t1", true, List.of(fact("a", "t1", "商务", "30"), fact("b", "t1", "业绩", "40"))),
                new ScoreTableSplitPolicy.ScoreTableInput("t2", true, List.of(fact("c", "t2", "技术", "30"))));
        var result = ScoreTableSplitPolicy.split(tables, List.of(), "本项目评分部分总分为100分。");
        assertThat(result.documentLevel().decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
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

    // ── 新增测试：0 分审查表不干扰评分表多表判定 ────────────────────────────────

    /**
     * 场景：一张真实评分表（weight 合计 = 100）+ 若干 weight 全为 0 的资格/形式审查表。
     * 预期：系统应识别为"实质单张评分表"，不触发 MULTI_TABLE_DECLARED_TOTAL_UNKNOWN，
     * 权重闭合时应直接 AUTO_APPROVED 而非 REVIEW_REQUIRED。
     * 对应生产根因：项目 1120 中国雅江集团采购项目中 t-0005/t-0006/t-0008/t-0028 均为 0 分审查表，
     * 导致系统误判为多表场景使 t-0007（真实评分表，weight sum = 100）被迫进入复核。
     */
    @Test
    void singleScoringTable_surroundedByZeroWeightAuditTables_notTreatedAsMultiTable() {
        // 模拟项目 1120 的表格结构：4 张资格/形式审查表（全 0 分）+ 1 张真正的评分表
        ScoreFact audit1 = fact("audit-f1", "t-audit1", "供应商名称", "0");
        ScoreFact audit2 = fact("audit-f2", "t-audit1", "报价函签字盖章", "0");
        ScoreFact audit3 = fact("audit-f3", "t-audit2", "营业执照", "0");
        ScoreFact score1 = fact("score-f1", "t-score", "商务部分", "20");
        ScoreFact score2 = fact("score-f2", "t-score", "技术部分", "30");
        ScoreFact score3 = fact("score-f3", "t-score", "报价部分", "50");

        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t-audit1", true, List.of(audit1, audit2)),
                new ScoreTableSplitPolicy.ScoreTableInput("t-audit2", true, List.of(audit3)),
                new ScoreTableSplitPolicy.ScoreTableInput("t-score", true, List.of(score1, score2, score3)));

        // 单张评分表无单独声明总分，依赖正文闭合 or 默认 100 兜底
        TableSplitResult result = ScoreTableSplitPolicy.split(tables, List.of(), null);

        // 关键断言：不应产生 MULTI_TABLE_DECLARED_TOTAL_UNKNOWN（即 multipleTables 应为 false）
        // 实质评分表（t-score, weight sum = 100）应走单表路径，默认 100 兜底 → AUTO_APPROVED
        assertThat(result.documentLevel().decision())
                .as("0分审查表不应触发多表判定，应为单张评分表路径（AUTO_APPROVED）")
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        // 验证没有任何表产生 MULTI_TABLE_DECLARED_TOTAL_UNKNOWN
        assertThat(result.tables()).noneMatch(t ->
                t.completeness() != null
                && t.completeness().fallbackReason() != null
                && t.completeness().fallbackReason().contains("MULTI_TABLE_DECLARED_TOTAL_UNKNOWN"));
    }

    /**
     * 场景：两张真实评分表（各有 weight > 0）+ 一张 0 分审查表。
     * 预期：只统计两张实质评分表 → multipleTables = true → 触发多表逻辑（无声明总分时 REVIEW_REQUIRED）。
     * 确保修复不会过度放行真正的多标包场景。
     */
    @Test
    void twoScoringTables_plusOneZeroWeightAuditTable_stillTreatedAsMultiTable() {
        ScoreFact audit = fact("audit-f1", "t-audit", "营业执照", "0");
        ScoreFact score1a = fact("s1-f1", "t-score1", "技术部分", "60");
        ScoreFact score1b = fact("s1-f2", "t-score1", "商务部分", "40");
        ScoreFact score2a = fact("s2-f1", "t-score2", "价格", "50");
        ScoreFact score2b = fact("s2-f2", "t-score2", "质量", "50");

        List<ScoreTableSplitPolicy.ScoreTableInput> tables = List.of(
                new ScoreTableSplitPolicy.ScoreTableInput("t-audit", true, List.of(audit)),
                new ScoreTableSplitPolicy.ScoreTableInput("t-score1", true, List.of(score1a, score1b)),
                new ScoreTableSplitPolicy.ScoreTableInput("t-score2", true, List.of(score2a, score2b)));

        TableSplitResult result = ScoreTableSplitPolicy.split(tables, List.of(), null);

        // 两张评分表无声明总分 → 仍然 REVIEW_REQUIRED
        assertThat(result.documentLevel().decision())
                .as("两张实质评分表（weight>0）应触发多表判定，无总分声明时 REVIEW_REQUIRED")
                .isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
    }
}
