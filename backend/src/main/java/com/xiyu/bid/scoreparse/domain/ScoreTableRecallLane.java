// Input: ScoreTableBlockLocator + ScoreTableRowParser（静态组合门面）
// Output: 召回五候选清单（一行 = 一候选，detail 为表格行级原文）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 FR-5 召回五

package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.CellAnchoredScoreExtractor;
import com.xiyu.bid.scoreparse.domain.grid.ColumnTypeCrossVerifier;
import com.xiyu.bid.scoreparse.domain.grid.HeuristicTableColumnMapper;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGrid;
import com.xiyu.bid.scoreparse.domain.grid.TableGridExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 召回五：评分表格行直抽（spec 055）。
 * <p>评分标准在招标文件中几乎总是表格；sidecar 已输出 markdown pipe 表格。
 * 本门面定位评分语义表格块并逐行解析为候选——行级候选天然完整、天然不重复，
 * 为全局归并裁决提供高质量输入。
 */
public final class ScoreTableRecallLane {

    private ScoreTableRecallLane() {
    }

    /** 全文 → 表格行候选；入参 null/无评分表格返回空列表（纯启发式） */
    public static List<ScoreCandidate> recall(String markdown) {
        return recall(markdown, null);
    }

    /** 全文 + 可选 Schema 映射器 → 表格行候选；若 mapper 为 null 或返回 null 则降级启发式 */
    public static List<ScoreCandidate> recall(String markdown, java.util.function.Function<TableGrid, TableColumnMapping> schemaMapper) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<ScoreCandidate> candidates = new ArrayList<>();

        // 1. 优先尝试 2D 网格单元格锚定抽取（spec 058）
        List<TableGrid> grids = TableGridExtractor.extractAll(markdown);
        for (TableGrid grid : grids) {
            if (grid.rowCount() >= 2 && grid.colCount() >= 2) {
                TableColumnMapping mapping = null;
                if (schemaMapper != null) {
                    try {
                        mapping = schemaMapper.apply(grid);
                    } catch (RuntimeException ignored) {
                        mapping = null;
                    }
                }
                if (mapping == null) {
                    mapping = HeuristicTableColumnMapper.infer(grid);
                }
                var vr = ColumnTypeCrossVerifier.verify(grid, mapping);
                if (vr.valid()) {
                    List<ScoreCandidate> gridCandidates = CellAnchoredScoreExtractor.extract(grid, mapping);
                    candidates.addAll(gridCandidates);
                }
            }
        }

        // 2. 若网格未抽取出候选，安全回退到逐行正则解析（spec 055 兜底防回退）
        if (candidates.isEmpty()) {
            for (ScoreTableBlockLocator.TableBlock block : ScoreTableBlockLocator.locate(markdown)) {
                for (String row : block.rows()) {
                    ScoreTableRowParser.parse(row).ifPresent(candidates::add);
                }
            }
        }
        return candidates;
    }
}
