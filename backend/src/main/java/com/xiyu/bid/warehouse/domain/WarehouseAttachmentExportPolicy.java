package com.xiyu.bid.warehouse.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 仓库附件导出策略：纯核心，判断指定附件类型是否应被导出。
 */
public final class WarehouseAttachmentExportPolicy {

    private WarehouseAttachmentExportPolicy() {}

    /**
     * 判断某个附件类型在当前导出范围下是否应被包含。
     *
     * @throws NullPointerException 当 scope 为 null 时
     */
    public static boolean isIncluded(WarehouseAttachmentExportScope scope, WarehouseAttachmentType type) {
        Objects.requireNonNull(scope, "attachmentScope must not be null");
        return switch (scope) {
            case WarehouseAttachmentExportScope.All all -> true;
            case WarehouseAttachmentExportScope.Partial partial -> partial.types().contains(type);
        };
    }

    /**
     * 按导出范围过滤附件，保持原 Map 结构（仓库 ID -> 附件列表）。
     */
    public static <A extends WarehouseAttachmentReadModel> Map<Long, List<A>> filter(
            WarehouseAttachmentExportScope scope,
            Map<Long, List<A>> attachmentsByWhId) {
        Objects.requireNonNull(scope, "attachmentScope must not be null");
        return attachmentsByWhId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .filter(a -> isIncluded(scope, a.getType()))
                                .toList(),
                        (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常
    }
}
