// Input: 结构校验器产物
// Output: 不可变 StructureValidationResult
// Pos: scoreparse/domain/grid — 纯核心（spec 059 Phase 1）
package com.xiyu.bid.scoreparse.domain.grid;

import java.util.List;

/**
 * CanonicalTableGrid 结构校验结果（spec 059 Phase 1 FR-04）。
 * <p>{@link #valid()} 为 true 时，校验器承诺：
 * <ul>
 *   <li>每个 logical (row, col) 坐标至多对应一个真实 cell 或一个指向 origin 的引用</li>
 *   <li>每个 mergedRegion.startCellId 指向同一 table 内的真实 origin cell</li>
 *   <li>cellId 全表唯一；行数 × 列数与 cell 总数一致（每个 slot 一个 cell）</li>
 *   <li>rowspan/colspan 不会越过表格边界</li>
 * </ul>
 * <p>{@link #valid()} 为 false 时：
 * <ul>
 *   <li>{@link #status()} 等于 {@link Status#STRUCTURE_INCOMPLETE}</li>
 *   <li>{@link #violations()} 列出每条违规（用 {@link Violation#field()} 作为定位键）</li>
 *   <li>调用方必须保留旧 {@code score_item}，不得落库新事实（Phase 4）</li>
 * </ul>
 */
public record StructureValidationResult(
        boolean valid,
        Status status,
        List<Violation> violations
) {
    public StructureValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        if (status == null) {
            status = valid ? Status.VALID : Status.STRUCTURE_INCOMPLETE;
        }
    }

    public static StructureValidationResult empty() {
        return new StructureValidationResult(true, Status.VALID, List.of());
    }

    public static StructureValidationResult ok() {
        return empty();
    }

    public static StructureValidationResult incomplete(List<Violation> violations) {
        return new StructureValidationResult(false, Status.STRUCTURE_INCOMPLETE, violations);
    }

    /** 总状态。 */
    public enum Status {
        /** 校验通过。 */
        VALID,
        /** 结构异常，无法作为评分事实来源（Phase 1 强制：不得调 LLM / 不得调归一化 / 不得落库）。 */
        STRUCTURE_INCOMPLETE
    }

    /**
     * 单条违规描述。{@code field} 是定位键（cellId 或路径），{@code reason} 是描述。
     */
    public record Violation(String field, String reason) {
        public Violation {
            if (field == null) {
                field = "";
            }
            if (reason == null) {
                reason = "";
            }
        }
    }
}
