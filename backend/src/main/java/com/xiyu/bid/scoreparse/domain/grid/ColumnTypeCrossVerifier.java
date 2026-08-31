// Input: TableGrid 数据与待验真的 TableColumnMapping
// Output: ValidationResult（可信度判断与错误原因）
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;

import java.util.regex.Pattern;

/**
 * 列类型交叉验真守卫（spec 058）。
 * <p>用于交叉验证大模型给出的列映射索引是否真实可靠：
 * <ul>
 *   <li>校验权重列：非空单元格至少 40% 具备合法分值特征（纯数字/百分比/X分/得X分/括号分值），且不允许全空；</li>
 *   <li>校验细则列：平均字符长度应合理，不能全为短数字或空值；</li>
 *   <li>校验边界：列索引必须处于有效范围 [0, colCount - 1]。</li>
 * </ul>
 */
public final class ColumnTypeCrossVerifier {

    private static final Pattern NUMERIC_WEIGHT_PATTERN =
            Pattern.compile("^[~≈]?\\d{1,3}(?:\\.\\d+)?\\s*(?:分|%|分值)?$");
    private static final Pattern BRACKET_WEIGHT_PATTERN = ScoreParseConstants.BRACKET_WEIGHT_PATTERN;
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
                // 纯分值单元格长度 <= 10；内嵌括号分值单元格（如"供应商业绩（7分）"，维度名使
                // 长度可达 35）单独走括号匹配分支——两分支均要求有分值特征，防细则列误判
                if ((weightCell.length() <= 10 && isPlausibleWeight(weightCell))
                        || (weightCell.length() <= 35 && BRACKET_WEIGHT_PATTERN.matcher(weightCell).find())) {
                    validWeightCount++;
                }
            }
        }

        // 空列穿透守卫：数据行存在但权重列全空 → 拒绝（防无效表格进入提取阶段）
        // 密度地板 0.4：dd6492143 曾为 MRO 括号列临时下调 0.35（PR !2481），括号分支计入
        // validWeightCount 后实测金标仍全绿，无需下调，恢复原地板（复审 Finding 4 口径）
        boolean hasNoValidWeight = validWeightCount == 0;
        boolean lowWeightDensity = nonEmptyRowCount > 0 && (double) validWeightCount / nonEmptyRowCount < 0.4;
        if (dataRowCount > 0 && (hasNoValidWeight || lowWeightDensity)) {
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
                || BRACKET_WEIGHT_PATTERN.matcher(trimmed).find()
                || GRADE_SCORE_PATTERN.matcher(trimmed).find()
                || trimmed.contains("分") || trimmed.contains("%");
    }
}
