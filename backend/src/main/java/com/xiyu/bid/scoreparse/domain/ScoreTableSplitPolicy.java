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
 *
 * <p><b>评分表判定规则</b>：仅统计包含至少一个 weight > 0 的末级评分项的表格作为独立评分表（
 * {@code scoringLeafBearingTablesCount}）。weight 全为 0 的资格/形式审查表不计入多表判定，
 * 避免非评分表格污染多表边界检测导致有效评分表被误判为 {@code MULTI_TABLE_DECLARED_TOTAL_UNKNOWN}。
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
        // 仅统计包含 weight > 0 末级评分项的表格作为有效评分表。
        // 资格审查表、形式审查表等 weight 全为 0 的合规性校验表不纳入多表计数，
        // 避免误将单张评分表判断成多表场景（MULTI_TABLE_DECLARED_TOTAL_UNKNOWN）。
        long scoringLeafBearingTablesCount = tablesById.values().stream()
                .filter(t -> isScoringTable(t, allParentIds))
                .count();
        // 兼容旧逻辑：若没有任何正权重末级项（全为 0 分表），回退到原始叶子计数防止空判。
        long leafBearingTablesCount = scoringLeafBearingTablesCount > 0 ? scoringLeafBearingTablesCount
                : tablesById.values().stream()
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
            boolean scoringTable = isScoringTable(table, allParentIds);
            if (ownTotals.isEmpty()) {
                if (multipleTables) {
                    if (scoringTable || scoringLeafBearingTablesCount == 0) {
                        results.add(new TableCompleteness(table.tableId(), false,
                                UnknownDeclaredTotalReviewPolicy.evaluateUnknownDeclaredTotal(table.facts(),
                                        "MULTI_TABLE_DECLARED_TOTAL_UNKNOWN"), List.of(table.tableId())));
                    }
                    continue;
                }
                if (scoringTable || scoringLeafBearingTablesCount == 0) {
                    noEvidenceFacts.addAll(table.facts());
                }
                continue;
            }
            if (scoringTable || scoringLeafBearingTablesCount == 0) {
                results.add(new TableCompleteness(table.tableId(), true,
                        ScoreCompletenessPolicy.evaluateCompleteness(table.facts(), tenderSectionText, true, ownTotals)));
            }
        }
        if (!noEvidenceFacts.isEmpty()) {
            List<String> noEvidenceTableIds = tablesById.values().stream()
                    .filter(table -> totalsByTable.getOrDefault(table.tableId(), List.of()).isEmpty()
                            && (scoringLeafBearingTablesCount == 0 || isScoringTable(table, allParentIds)))
                    .map(ScoreTableInput::tableId)
                    .toList();
            results.add(new TableCompleteness(NO_DECLARED_TOTAL, false,
                    ScoreCompletenessPolicy.evaluateCompleteness(noEvidenceFacts, tenderSectionText, true, List.of()),
                    noEvidenceTableIds));
        }
        if (multipleTables && results.stream().allMatch(result -> result.completeness() != null
                && result.completeness().decision() == ScoreParseDecision.REVIEW_REQUIRED
                && result.completeness().fallbackReason() != null
                && result.completeness().fallbackReason().contains("DECLARED_TOTAL_UNKNOWN"))) {
            var sourceTotal = TextScoringTotalEvidence.textTotal(tenderSectionText);
            if (sourceTotal.value() != null && sourceTotal.quote().matches(
                    "(?s).*(?:本项目|本标包)[^\\n]{0,40}(?:总分|满分)\\s*(?:为|：|:)\\s*\\d+(?:\\.\\d+)?\\s*分.*")) {
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

    private static boolean isScoringTable(ScoreTableInput table, java.util.Set<String> allParentIds) {
        return table != null && table.facts().stream().anyMatch(f ->
                !allParentIds.contains(f.factId())
                && f.weight() != null
                && f.weight().compareTo(BigDecimal.ZERO) > 0);
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
