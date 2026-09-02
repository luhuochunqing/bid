// Input: Sidecar DocumentArtifact mergedRegion 数组
// Output: 不可变 MergedRegion 实体
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖（spec 059 Phase 1）
package com.xiyu.bid.scoreparse.domain.grid;

import java.util.Optional;

/**
 * 合并区域（spec 059 Phase 1）。
 * <p>对应一个 origin cell 的 rowspan × colspan 矩形范围。同一 origin 在合并区域内只允许
 * 出现一次；其余 logical cell 必须以 {@link CanonicalCell#isMergedContinue()} 为 true
 * 标记。
 *
 * <h3>不变量的处理</h3>
 * <p>FP-Java 原则：domain 层不抛业务异常。本 record 的硬不变量通过 {@link #validate()}
 * 暴露，调用方（如 {@link CanonicalTableGridFactory}）将违规收集到
 * {@code StructureValidationResult.violations}，而不是让构造抛出异常。
 */
public record MergedRegion(String startCellId, int rowspan, int colspan) {

    public MergedRegion {
        if (startCellId == null) {
            startCellId = "";
        }
    }

    public boolean isSingleRow() {
        return rowspan == 1;
    }

    public boolean isSingleColumn() {
        return colspan == 1;
    }

    /**
     * 验证硬不变量。返回 {@code Optional.empty()} 表示通过；{@code Optional.of(reason)}
     * 表示失败及原因。
     */
    public Optional<String> validate() {
        if (startCellId.isBlank()) {
            return Optional.of("startCellId is required");
        }
        if (rowspan < 1) {
            return Optional.of("rowspan must be >= 1 but was " + rowspan);
        }
        if (colspan < 1) {
            return Optional.of("colspan must be >= 1 but was " + colspan);
        }
        if (rowspan == 1 && colspan == 1) {
            return Optional.of(
                    "MergedRegion with rowspan=1 colspan=1 is a no-op; "
                            + "remove from DocumentArtifact");
        }
        return Optional.empty();
    }
}
