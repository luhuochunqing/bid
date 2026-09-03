// Input: Sidecar DocumentArtifact 单个 cell JSON
// Output: 不可变 CanonicalCell 实体
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖（spec 059 Phase 1）
package com.xiyu.bid.scoreparse.domain.grid;

import java.util.Optional;

/**
 * 规范单元格（spec 059 Phase 1）。
 * <p>与 {@code TableGrid} 的字符串矩阵不同，本模型同时携带：
 * <ul>
 *   <li>{@code cellId} — 全局唯一，格式 {@code t-NNNN-rR-cC}</li>
 *   <li>{@code rawText} / {@code normalizedText} — 双文本保留</li>
 *   <li>{@code rowspan} / {@code colspan} — 合并跨度</li>
 *   <li>{@code isMergedContinue} — 合并延续标记</li>
 *   <li>{@code sourceXmlPath} — Word XML 路径锚点，可回溯到原始 docx</li>
 * </ul>
 * <p>{@code isMergedContinue=true} 的 cell 通常不应作为评分事实的来源；
 * 评分事实验证只能绑定到 origin cell（isMergedContinue=false 且 rowspan/colspan 合理）。
 *
 * <h3>不变量的处理</h3>
 * <p>FP-Java 原则：domain 层不抛业务异常（参见 {@code FPJavaArchitectureTest.
 * pure_core_must_not_use_exceptions_for_business_flow}）。CanonicalCell
 * 本身作为值对象接受任意输入并把 null 规范化成空串；硬不变量（rowspan&gt;=1、
 * colspan&gt;=1、坐标非负）通过 {@link #validate()} 返回 {@code Optional<String>}
 * 描述违规原因；调用方（如 {@link CanonicalTableGridFactory}）把违规收集到
 * {@code StructureValidationResult.violations}，而不是让业务流抛异常。
 */
public record CanonicalCell(
        String cellId,
        int row,
        int column,
        String rawText,
        String normalizedText,
        int rowspan,
        int colspan,
        boolean isMergedContinue,
        String sourceXmlPath
) {
    public CanonicalCell {
        cellId = cellId == null ? "" : cellId;
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        sourceXmlPath = sourceXmlPath == null ? "" : sourceXmlPath;
    }

    /** True if this cell can be cited as the source cell of a score fact. */
    public boolean isUsableOrigin() {
        return !isMergedContinue() && !rawText().isBlank();
    }

    /**
     * 验证硬不变量。返回 {@code Optional.empty()} 表示通过；{@code Optional.of(reason)}
     * 表示失败及原因。
     * <p>校验规则：
     * <ul>
     *   <li>cellId 非空</li>
     *   <li>row/column ≥ 0</li>
     *   <li>rowspan ≥ 1 且 colspan ≥ 1</li>
     * </ul>
     */
    public Optional<String> validate() {
        if (cellId.isBlank()) {
            return Optional.of("cellId is required");
        }
        if (row < 0) {
            return Optional.of("row must be non-negative for cell " + cellId);
        }
        if (column < 0) {
            return Optional.of("column must be non-negative for cell " + cellId);
        }
        if (rowspan < 1) {
            return Optional.of("rowspan must be >= 1 but was " + rowspan + " for cell " + cellId);
        }
        if (colspan < 1) {
            return Optional.of("colspan must be >= 1 but was " + colspan + " for cell " + cellId);
        }
        return Optional.empty();
    }
}
