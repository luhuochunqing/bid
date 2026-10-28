// Input: 多张评分表分组事实 + 全表汇总声明 + 正文
// Output: 每张独立表完整性结果 + 文档级聚合（决策取最差）
// Pos: scoreparse/domain — 纯核心，零框架依赖（spec 062 方案 A）
package com.xiyu.bid.scoreparse.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/**
 * 多表评分标准自动拆分策略（spec 062 方案 A）。
 * <p>按 {@code sourceTableId} 把评分事实拆分为多个评分标准：有本表结构化总分证据的表
 * 独立调 {@link ScoreCompletenessPolicy} 校验；单表无证据时走正文/默认 100 兜底，多表无证据时逐表保留未知口径；
 * 结构破损表按 {@code STRUCTURE_INCOMPLETE} 单独标记。文档级聚合取最差决策，
 * 但不阻塞按表落库（调用方按表聚合候选）。
 */
public final class ScoreTableSplitPolicy {

    public static final String NO_DECLARED_TOTAL = "NO_DECLARED_TOTAL";
    private ScoreTableSplitPolicy() {
    }

    public static TableSplitResult split(
            List<ScoreTableInput> tables,
            List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> declaredTotals,
            String tenderSectionText) {
        Map<String, ScoreTableInput> tablesById = new LinkedHashMap<>();
        for (ScoreTableInput table : tables) {
            if (table != null && !table.tableId().isBlank()) {
                tablesById.put(table.tableId(), table);
            }
        }
        Map<String, List<ScoreTableSummaryPolicy.DeclaredTotalEvidence>> totalsByTable = new LinkedHashMap<>();
        for (ScoreTableSummaryPolicy.DeclaredTotalEvidence evidence : declaredTotals) {
            if (evidence != null && evidence.tableId() != null && !evidence.tableId().isBlank()) {
                totalsByTable.computeIfAbsent(evidence.tableId(), key -> new ArrayList<>()).add(evidence);
            }
        }

        List<TableCompleteness> results = new ArrayList<>();
        List<ScoreFact> noEvidenceFacts = new ArrayList<>();
        java.util.Set<String> allParentIds = tablesById.values().stream()
                .flatMap(t -> t.facts().stream())
                .map(ScoreFact::parentFactId)
                .filter(pid -> pid != null && !pid.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        long leafBearingTablesCount = tablesById.values().stream()
                .filter(t -> t.facts().stream().anyMatch(f -> !allParentIds.contains(f.factId())))
                .count();
        boolean multipleTables = (leafBearingTablesCount > 1) || (tablesById.size() > 1 && leafBearingTablesCount == 0);
        for (ScoreTableInput table : tablesById.values()) {
            if (!table.structurallyValid()) {
                results.add(new TableCompleteness(table.tableId(), false,
                        ScoreCompletenessPolicy.evaluateCompleteness(List.of(), tenderSectionText, false)));
                continue;
            }
            List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> ownTotals =
                    totalsByTable.getOrDefault(table.tableId(), List.of());
            if (ownTotals.isEmpty()) {
                if (multipleTables) {
                    results.add(new TableCompleteness(table.tableId(), false,
                            UnknownDeclaredTotalReviewPolicy.evaluateUnknownDeclaredTotal(table.facts(),
                                    "MULTI_TABLE_DECLARED_TOTAL_UNKNOWN"), List.of(table.tableId())));
                    continue;
                }
                noEvidenceFacts.addAll(table.facts());
                continue;
            }
            results.add(new TableCompleteness(table.tableId(), true,
                    ScoreCompletenessPolicy.evaluateCompleteness(table.facts(), tenderSectionText, true, ownTotals)));
        }
        if (!noEvidenceFacts.isEmpty()) {
            results.add(new TableCompleteness(NO_DECLARED_TOTAL, false,
                    ScoreCompletenessPolicy.evaluateCompleteness(noEvidenceFacts, tenderSectionText, true, List.of()),
                    tablesById.values().stream()
                            .filter(table -> totalsByTable.getOrDefault(table.tableId(), List.of()).isEmpty())
                            .map(ScoreTableInput::tableId)
                            .toList()));
        }
        if (multipleTables && results.stream().allMatch(result -> result.completeness() != null
                && result.completeness().decision() == ScoreParseDecision.REVIEW_REQUIRED
                && result.completeness().fallbackReason() != null
                && result.completeness().fallbackReason().contains("DECLARED_TOTAL_UNKNOWN"))) {
            var sourceTotal = OriginalScoringContext.textTotal(tenderSectionText);
            if (sourceTotal.value() != null) {
                BigDecimal declared = sourceTotal.value();
                List<ScoreFact> allFacts = tablesById.values().stream().flatMap(table -> table.facts().stream()).toList();
                BigDecimal leafTotal = ScoreUnitPolicy.totalWeight(allFacts.stream().map(fact ->
                        new ScoreUnitPolicy.Node(fact.factId(), fact.parentFactId(), fact.weight(), fact.childAggregation().name())).toList());
                if (leafTotal.compareTo(declared) == 0) {
                    var closed = ScoreCompletenessPolicy.evaluateCompleteness(allFacts, tenderSectionText, true,
                            List.of(new ScoreTableSummaryPolicy.DeclaredTotalEvidence(declared, "DOCUMENT", 0,
                                    sourceTotal.locator(), sourceTotal.quote())));
                    if (closed.decision() == ScoreParseDecision.AUTO_APPROVED) {
                        results.clear();
                        results.add(new TableCompleteness("DOCUMENT", true, closed,
                                tablesById.keySet().stream().toList()));
                    }
                }
            }
        }
        return new TableSplitResult(List.copyOf(results),
                aggregateDocumentLevel(results, tenderSectionText));
    }

    private static ScoreCompletenessPolicy.ScoreCompletenessResult aggregateDocumentLevel(
            List<TableCompleteness> results, String tenderSectionText) {
        ScoreCompletenessPolicy.ScoreCompletenessResult worst = null;
        for (TableCompleteness table : results) {
            ScoreCompletenessPolicy.ScoreCompletenessResult current = table.completeness();
            if (current == null) {
                continue;
            }
            if (worst == null || decisionRank(current.decision()) < decisionRank(worst.decision())) {
                worst = current;
            }
        }
        if (worst == null) {
            return ScoreCompletenessPolicy.evaluateCompleteness(List.of(), tenderSectionText, true, List.of());
        }
        return worst;
    }

    private static int decisionRank(ScoreParseDecision decision) {
        if (decision == null) {
            return 4;
        }
        return switch (decision) {
            case PARSE_FAILED -> 0;
            case STRUCTURE_INCOMPLETE -> 1;
            case REVIEW_REQUIRED -> 2;
            case AUTO_APPROVED, MANUAL_APPROVED -> 3;
        };
    }

    /**
     * @param tableId          来源评分表 ID（分组键）
     * @param structurallyValid 结构校验是否通过（失败表 facts 恒为空）
     * @param facts            该表抽取的评分事实
     */
    public record ScoreTableInput(
            String tableId,
            boolean structurallyValid,
            List<ScoreFact> facts) {
        public ScoreTableInput {
            tableId = tableId == null ? "" : tableId;
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }
}
