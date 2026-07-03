package com.xiyu.bid.warehouse.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓库附件导出策略：纯核心，判断指定附件类型是否应被导出。
 */
public final class WarehouseAttachmentExportPolicy {

    private WarehouseAttachmentExportPolicy() {}

    /**
     * 判断某个附件类型在当前导出范围下是否应被包含。
     */
    public static boolean isIncluded(WarehouseAttachmentExportScope scope, WarehouseAttachmentType type) {
        return switch (scope) {
            case WarehouseAttachmentExportScope.All all -> true;
            case WarehouseAttachmentExportScope.None none -> false;
            case WarehouseAttachmentExportScope.Partial partial -> partial.types().contains(type);
        };
    }

    /**
     * 按导出范围过滤附件，保持原 Map 结构（仓库 ID -> 附件列表）。
     */
    public static <A extends WarehouseAttachmentReadModel> Map<Long, List<A>> filter(
            WarehouseAttachmentExportScope scope,
            Map<Long, List<A>> attachmentsByWhId) {
        return attachmentsByWhId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .filter(a -> isIncluded(scope, a.getType()))
                                .toList()
                ));
    }
}
