// Input: TableGrid 数据与待验真的 TableColumnMapping
// Output: ValidationResult（可信度判断与错误原因）
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import java.util.regex.Pattern;

/**
 * 列类型交叉验真守卫（spec 058）。
 * <p>用于交叉验证大模型给出的列映射索引是否真实可靠：
 * <ul>
 *   <li>校验权重列：必须包含至少 50% 合法分值（纯数字/百分比/X分/得X分）；</li>
 *   <li>校验细则列：平均字符长度应合理，不能全为短数字或空值；</li>
 *   <li>校验边界：列索引必须处于有效范围 [0, colCount - 1]。</li>
 * </ul>
 */
public final class ColumnTypeCrossVerifier {

    private static final Pattern NUMERIC_WEIGHT_PATTERN =
            Pattern.compile("^[~≈]?\\d{1,3}(?:\\.\\d+)?\\s*(?:分|%|分值)?$");
    private static final Pattern GRADE_SCORE_PATTERN =
            Pattern.compile("得\\s*\\d{1,3}(?:\\.\\d+)?\\s*分");

    private ColumnTypeCrossVerifier() {
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "OK");
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    public static ValidationResult verify(TableGrid grid, TableColumnMapping mapping) {
        if (grid == null || grid.isEmpty()) {
            return ValidationResult.invalid("表格为空");
        }
        if (mapping == null) {
            return ValidationResult.invalid("列映射为空");
        }

        int colCount = grid.colCount();
        int rowCount = grid.rowCount();
        int headerRows = mapping.headerRows();

        if (headerRows >= rowCount) {
            return ValidationResult.invalid("表头行数超过总行数");
        }

        // 1. 索引边界检查
        if (mapping.weightCol() < 0 || mapping.weightCol() >= colCount) {
            return ValidationResult.invalid("权重列索引超出范围: " + mapping.weightCol());
        }
        if (mapping.detailCol() < 0 || mapping.detailCol() >= colCount) {
            return ValidationResult.invalid("细则列索引超出范围: " + mapping.detailCol());
        }
        if (mapping.codeCol() >= colCount) {
            return ValidationResult.invalid("编号列索引超出范围: " + mapping.codeCol());
        }
        if (mapping.dimCol() >= colCount) {
            return ValidationResult.invalid("维度列索引超出范围: " + mapping.dimCol());
        }

        // 2. 权重列类型采样验真
        int dataRowCount = rowCount - headerRows;
        int validWeightCount = 0;
        int nonEmptyRowCount = 0;

        for (int r = headerRows; r < rowCount; r++) {
            String weightCell = grid.cell(r, mapping.weightCol());
            if (!weightCell.isBlank()) {
                nonEmptyRowCount++;
                // 权重单元格长度超过 10 字符（如"具备甲级资质得5分"）说明实际上是细则列而非纯分值列
                if (weightCell.length() <= 10 && isPlausibleWeight(weightCell)) {
                    validWeightCount++;
                }
            }
        }

        // 权重列中非空单元格至少有 40% 具备分值特征（防全表列错位）
        if (nonEmptyRowCount > 0 && (double) validWeightCount / nonEmptyRowCount < 0.4) {
            return ValidationResult.invalid("权重列有效分值占比过低 (" + validWeightCount + "/" + nonEmptyRowCount + ")，列映射不可信");
        }

        // 3. 细则列长度采样验真：细则列平均长度必须 >= 4 个字符
        int totalDetailLen = 0;
        for (int r = headerRows; r < rowCount; r++) {
            totalDetailLen += grid.cell(r, mapping.detailCol()).length();
        }
        double avgDetailLen = dataRowCount > 0 ? (double) totalDetailLen / dataRowCount : 0;
        if (avgDetailLen < 4.0 && dataRowCount > 0) {
            return ValidationResult.invalid("细则列平均长度过短 (" + avgDetailLen + ")，列映射不可信");
        }

        return ValidationResult.ok();
    }

    private static boolean isPlausibleWeight(String cell) {
        String trimmed = cell.trim();
        return NUMERIC_WEIGHT_PATTERN.matcher(trimmed).matches()
                || GRADE_SCORE_PATTERN.matcher(trimmed).find()
                || trimmed.contains("分") || trimmed.contains("%");
    }
}
