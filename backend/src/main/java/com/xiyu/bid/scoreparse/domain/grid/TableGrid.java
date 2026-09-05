// Input: 2D 网格数据（行列表与单元格文本）
// Output: 结构化 TableGrid 不变实体
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 二维表格网格实体（spec 058）。
 * <p>表示结构化的 Row x Col 矩阵，保留物理行列坐标与单元格原始文本，
 * 支持纵向合并补全（Fill-down）与行列切片。
 */
public final class TableGrid {

    private final List<List<String>> rows;
    private final int rowCount;
    private final int colCount;

    public TableGrid(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            this.rows = List.of();
            this.rowCount = 0;
            this.colCount = 0;
            return;
        }
        int maxCols = 0;
        for (List<String> row : rows) {
            if (row != null) {
                maxCols = Math.max(maxCols, row.size());
            }
        }
        List<List<String>> copy = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            if (row == null) {
                copy.add(List.of());
            } else if (row.size() < maxCols && isRightAlignedRow(row)) {
                List<String> padded = new ArrayList<>(maxCols);
                int padCount = maxCols - row.size();
                for (int i = 0; i < padCount; i++) {
                    padded.add("");
                }
                padded.addAll(row);
                copy.add(Collections.unmodifiableList(padded));
            } else {
                List<String> rowCopy = new ArrayList<>(row);
                while (rowCopy.size() < maxCols) {
                    rowCopy.add("");
                }
                copy.add(Collections.unmodifiableList(rowCopy));
            }
        }
        this.rows = Collections.unmodifiableList(copy);
        this.rowCount = this.rows.size();
        this.colCount = maxCols;
    }

    private static boolean isRightAlignedRow(List<String> row) {
        if (row.isEmpty()) {
            return false;
        }
        String first = row.get(0).trim();
        // 若第一列本身已具备编号特征（纯数字、字母编号、中文数字章节如一/二、顿号或括号标号），禁止左补空
        if (first.matches("^\\d+(?:[-—.]\\d+)*[、.．]?$")
                || first.matches("^[A-Za-z]\\d+$")
                || first.matches("^[一二三四五六七八九十百]+[、.．]?$")
                || first.matches("^[（(][\\d一二三四五六七八九十]+[)）]$")) {
            return false;
        }
        String last = row.get(row.size() - 1).trim();
        // 尾列必须为合法的非负分值权重（0-100），排除负数扣分（如 -10）及非分值文本
        if (!last.matches("^[~≈]?\\d{1,3}(?:\\.\\d+)?\\s*(?:分|%)?$")) {
            return false;
        }
        String numStr = last.replaceAll("[^0-9.]", "");
        if (numStr.isEmpty()) {
            return false;
        }
        int dotIdx = numStr.indexOf('.');
        String intPart = dotIdx >= 0 ? numStr.substring(0, dotIdx) : numStr;
        if (intPart.length() > 3 || (intPart.length() == 3 && !intPart.equals("100"))) {
            return false;
        }
        if (intPart.length() == 3 && dotIdx >= 0 && !numStr.substring(dotIdx + 1).matches("^0+$")) {
            return false;
        }
        return true;
    }

    public int rowCount() {
        return rowCount;
    }

    public int colCount() {
        return colCount;
    }

    public boolean isEmpty() {
        return rowCount == 0 || colCount == 0;
    }

    public String cell(int row, int col) {
        if (row < 0 || row >= rowCount || col < 0) {
            return "";
        }
        List<String> r = rows.get(row);
        if (col >= r.size()) {
            return "";
        }
        String val = r.get(col);
        return val == null ? "" : val.trim();
    }

    public List<String> row(int row) {
        if (row < 0 || row >= rowCount) {
            return List.of();
        }
        return rows.get(row);
    }

    public List<String> column(int col) {
        if (col < 0 || col >= colCount) {
            return List.of();
        }
        List<String> colValues = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            colValues.add(cell(r, col));
        }
        return colValues;
    }

    public List<List<String>> allRows() {
        return rows;
    }
}
