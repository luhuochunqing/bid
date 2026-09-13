// Input: Sidecar DocumentArtifact v2
// Output: Artifact→CanonicalTableGrid→ScoreFact 的确定性影子执行结果
// Pos: scoreparse/application — 只读影子组装管道（审核修复：孤岛类端到端验证）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreTableSplitPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.TableSplitResult;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Artifact → CanonicalTableGrid → ScoreFact 组装管道（spec 059）。
 * <p>把 Phase 1 的结构契约与 Phase 2 的确定性抽取串成一条可端到端验证的
 * <b>纯转换/校验</b>管道：不落库、不调 LLM、不调归一化。生产主链路调用本管道后，
 * 仍由 application 层根据 {@link ScoreCompletenessPolicy.ScoreCompletenessResult}
 * 决定是否覆盖兼容 {@code score_item}。本管道承担：
 * <ul>
 *   <li>影子执行验证器——Sidecar 抽取结果可随时用本管道比对确定性产物；</li>
 *   <li>迁移前对照基线——金标语料（docs/lessons/scoreparse-golden-corpus）
 *       的只读回放口径。</li>
 * </ul>
 * <p>多表拆分（spec 062）：自 062 起管道按 {@link ScoreTableSplitPolicy} 先按表分组独立校验，
 * 再聚合文档级结果；{@code completeness} 恒为文档级聚合值，新增
 * {@code tableCompletenesses} 承载每张独立评分表的明细。
 * <p>纯静态、无框架依赖：输入产出全部是不可变值对象（FP-Java）。
 */
public final class ScoreFactShadowPipeline {

    private ScoreFactShadowPipeline() {
    }

    /**
     * 对整份 DocumentArtifact 做影子执行。
     * <p>仅处理 {@code isScoreTable=true} 的表；结构校验失败的表保留在
     * {@link TableOutcome} 中（{@code structurallyValid=false}、facts 为空），
     * 由调用方按 {@code STRUCTURE_INCOMPLETE} 处理——绝不回退旧字符串表格。
     */
    public static ShadowResult run(DocumentArtifact artifact) {
        return run(artifact, null);
    }

    public static ShadowResult run(DocumentArtifact artifact, String tenderSectionText) {
        if (artifact == null) {
            return new ShadowResult(List.of(), 0,
                    new ScoreFactValidator.ValidationResult(false, false, List.of("artifact is null")),
                    ScoreCompletenessPolicy.evaluateCompleteness(List.of(), tenderSectionText, false),
                    List.of());
        }
        List<TableOutcome> outcomes = new ArrayList<>();
        int skipped = 0;
        for (DocumentArtifact.CanonicalTable table : artifact.tables()) {
            if (table == null || !Boolean.TRUE.equals(table.isScoreTable())) {
                skipped++;
                continue;
            }
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(table);
            TableColumnMapping mapping = HeuristicTableColumnMapper.infer(toLegacyGrid(grid));
            List<ScoreFact> facts = grid.isStructurallyValid()
                    ? DeterministicScoreFactExtractor.extract(grid, mapping)
                    : List.of();
            outcomes.add(new TableOutcome(grid, mapping, facts, grid.isStructurallyValid()));
        }
        List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> declaredTotals = collectDeclaredTotals(outcomes);
        TableSplitResult split = splitOutcomes(outcomes, declaredTotals, tenderSectionText);
        List<ScoreFact> allFacts = outcomes.stream()
                .flatMap(outcome -> outcome.facts().stream())
                .toList();
        return new ShadowResult(List.copyOf(outcomes), skipped,
                ScoreFactValidator.validateScoringSet(allFacts),
                split.documentLevel(), split.tables());
    }

    private static TableSplitResult splitOutcomes(
            List<TableOutcome> outcomes,
            List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> declaredTotals,
            String tenderSectionText) {
        List<ScoreTableSplitPolicy.ScoreTableInput> inputs = outcomes.stream()
                .map(outcome -> new ScoreTableSplitPolicy.ScoreTableInput(
                        outcome.grid().tableId(), outcome.structurallyValid(), outcome.facts()))
                .toList();
        return ScoreTableSplitPolicy.split(inputs, declaredTotals, tenderSectionText);
    }

    private static List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> collectDeclaredTotals(
            List<TableOutcome> outcomes) {
        return outcomes.stream()
                .flatMap(outcome -> ScoreTableSummaryPolicy.extractDeclaredTotals(
                        outcome.grid(), outcome.mapping()).stream())
                .toList();
    }

    /**
     * 把 CanonicalTableGrid 的 origin 文本投影为旧 {@link TableGrid}，
     * 供 {@link HeuristicTableColumnMapper} 推断列语义。continuation 一律
     * 记空串，保证列坐标与 Sidecar 网格一一对应。
     */
    private static TableGrid toLegacyGrid(CanonicalTableGrid grid) {
        List<List<String>> rows = new ArrayList<>(grid.rows());
        for (int r = 0; r < grid.rows(); r++) {
            List<String> row = new ArrayList<>(grid.columns());
            for (int c = 0; c < grid.columns(); c++) {
                row.add("");
            }
            rows.add(row);
        }
        grid.cells().forEach(cell -> {
            if (cell.row() < grid.rows() && cell.column() < grid.columns()) {
                rows.get(cell.row()).set(cell.column(), cell.isMergedContinue() ? "" : cell.rawText());
            }
        });
        return new TableGrid(rows);
    }

    /**
     * @param grid              结构校验后的规范化网格
     * @param mapping           由表头文本启发式推断的列映射
     * @param facts             确定性抽取的事实（结构失败时为空）
     * @param structurallyValid 结构校验是否通过
     */
    public record TableOutcome(
            CanonicalTableGrid grid,
            TableColumnMapping mapping,
            List<ScoreFact> facts,
            boolean structurallyValid) {
    }

    /**
     * @param tables             每个评分表的影子执行结果
     * @param skippedTables      非评分表数量
     * @param scoring            全量事实池的自动评分集校验
     * @param completeness       文档级完整性聚合（最差决策，spec 062）
     * @param tableCompletenesses 每张独立评分表的完整性结果（spec 062）
     */
    public record ShadowResult(
            List<TableOutcome> tables,
            int skippedTables,
            ScoreFactValidator.ValidationResult scoring,
            ScoreCompletenessPolicy.ScoreCompletenessResult completeness,
            List<TableCompleteness> tableCompletenesses) {

    }
}
