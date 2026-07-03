package com.xiyu.bid.warehouse.domain;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库附件导出范围：纯核心值对象。
 */
public sealed interface WarehouseAttachmentExportScope {

    record All() implements WarehouseAttachmentExportScope {}

    record None() implements WarehouseAttachmentExportScope {}

    record Partial(Set<WarehouseAttachmentType> types) implements WarehouseAttachmentExportScope {
        public Partial {
            types = Set.copyOf(types);
        }
    }

    /**
     * 从协议层字符串构造导出范围。
     *
     * @param scope     ALL | NONE | PARTIAL，大小写不敏感
     * @param typeNames PARTIAL 时使用的类型名称集合
     * @return 合法时返回对应的 scope，非法时返回 Optional.empty()
     */
    static Optional<WarehouseAttachmentExportScope> from(String scope, Set<String> typeNames) {
        if (scope == null || scope.isBlank()) {
            return Optional.of(new All());
        }
        return switch (scope.trim().toUpperCase()) {
            case "ALL" -> Optional.of(new All());
            case "NONE" -> Optional.of(new None());
            case "PARTIAL" -> parsePartial(typeNames);
            default -> Optional.empty();
        };
    }

    private static Optional<WarehouseAttachmentExportScope> parsePartial(Set<String> typeNames) {
        if (typeNames == null || typeNames.isEmpty()) {
            return Optional.empty();
        }
        Set<WarehouseAttachmentType> types = typeNames.stream()
                .map(WarehouseAttachmentExportScope::parseType)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        if (types.size() != typeNames.size()) {
            return Optional.empty();
        }
        return Optional.of(new Partial(types));
    }

    private static Optional<WarehouseAttachmentType> parseType(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(WarehouseAttachmentType.valueOf(name.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
