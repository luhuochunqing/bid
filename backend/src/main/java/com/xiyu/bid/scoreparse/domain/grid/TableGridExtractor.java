// Input: Markdown 原始表格文本
// Output: 结构化 TableGrid（去除分隔线、清洗首尾管道符）
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * 2D 表格网格抽取器（spec 058）。
 * <p>将 Markdown 管道表格解析为结构化的 {@link TableGrid}，
 * 自动识别并跳过 Markdown 对齐分隔行（{@code |---|---|:---|}）。
 */
public final class TableGridExtractor {

    private TableGridExtractor() {
    }

    /**
     * 从 Markdown 文本中抽取首个有效评分表格网格。
     *
     * @param markdown Markdown 文本
     * @return TableGrid 实例（若无有效表格则返回空 Grid）
     */
    public static TableGrid extract(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new TableGrid(List.of());
        }
        String[] lines = markdown.split("\\R");
        List<List<String>> gridRows = new ArrayList<>();
        boolean inTable = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (isTableLine(trimmed)) {
                if (isSeparatorLine(trimmed)) {
                    // 分隔行，跳过但保持 inTable = true
                    inTable = true;
                    continue;
                }
                List<String> cells = parseRowCells(trimmed);
                if (!cells.isEmpty()) {
                    gridRows.add(cells);
                    inTable = true;
                }
            } else if (inTable) {
                // 若已在表格中遇到非表格行，且已有足够行数，则结束当前表格抽取
                if (gridRows.size() >= 2) {
                    break;
                }
            }
        }
        return new TableGrid(gridRows);
    }

    /**
     * 将包含多张表格的 Markdown 抽取为多个 TableGrid 列表。
     */
    public static List<TableGrid> extractAll(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String[] lines = markdown.split("\\R");
        List<TableGrid> result = new ArrayList<>();
        List<List<String>> currentGrid = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (isTableLine(trimmed)) {
                if (!isSeparatorLine(trimmed)) {
                    List<String> cells = parseRowCells(trimmed);
                    if (!cells.isEmpty()) {
                        currentGrid.add(cells);
                    }
                }
            } else {
                if (currentGrid.size() >= 2) {
                    result.add(new TableGrid(currentGrid));
                    currentGrid = new ArrayList<>();
                } else {
                    currentGrid.clear();
                }
            }
        }
        if (currentGrid.size() >= 2) {
            result.add(new TableGrid(currentGrid));
        }
        return result;
    }

    private static boolean isTableLine(String line) {
        return line != null && line.startsWith("|") && line.endsWith("|");
    }

    private static boolean isSeparatorLine(String line) {
        if (line == null) return false;
        String content = line.replace("|", "").replace("-", "").replace(":", "").replace(" ", "");
        return content.isEmpty();
    }

    private static List<String> parseRowCells(String rowLine) {
        List<String> cells = new ArrayList<>();
        // 去除首尾的 '|'
        String inner = rowLine;
        if (inner.startsWith("|")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        String[] parts = inner.split("\\|", -1);
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }
}
