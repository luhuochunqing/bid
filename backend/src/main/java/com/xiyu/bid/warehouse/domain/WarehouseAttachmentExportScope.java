package com.xiyu.bid.warehouse.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库附件导出范围：纯核心值对象。
 * 仅支持 ALL（全部导出）与 PARTIAL（按指定类型导出）。
 */
public sealed interface WarehouseAttachmentExportScope {

    record All() implements WarehouseAttachmentExportScope {}

    record Partial(Set<WarehouseAttachmentType> types) implements WarehouseAttachmentExportScope {
        public Partial {
            types = Set.copyOf(types);
        }
    }

    /**
     * 从协议层字符串构造导出范围。
     *
     * @param scope     ALL | PARTIAL，大小写不敏感
     * @param typeNames PARTIAL 时使用的类型名称集合
     * @return 合法的导出范围
     * @throws IllegalArgumentException 当 scope 非法、PARTIAL 未指定类型或包含未知类型时
     */
    static WarehouseAttachmentExportScope from(String scope, Set<String> typeNames) {
        if (scope == null || scope.isBlank()) {
            return new All();
        }
        return switch (scope.trim().toUpperCase()) {
            case "ALL" -> new All();
            case "PARTIAL" -> parsePartial(typeNames);
            default -> throw new IllegalArgumentException("附件导出范围非法，仅支持 ALL 或 PARTIAL: " + scope);
        };
    }

    private static WarehouseAttachmentExportScope parsePartial(Set<String> typeNames) {
        if (typeNames == null || typeNames.isEmpty()) {
            throw new IllegalArgumentException("部分导出时必须至少指定一种附件类型");
        }
        Set<String> normalized = typeNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("部分导出时必须至少指定一种附件类型");
        }
        Set<WarehouseAttachmentType> types = normalized.stream()
                .map(WarehouseAttachmentExportScope::parseType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (types.size() != normalized.size()) {
            Set<String> validNames = types.stream()
                    .map(WarehouseAttachmentType::name)
                    .collect(Collectors.toSet());
            Set<String> invalid = normalized.stream()
                    .filter(n -> !validNames.contains(n.toUpperCase()))
                    .collect(Collectors.toSet());
            throw new IllegalArgumentException("未知的附件类型: " + String.join(", ", invalid)
                    + "，可选值: " + java.util.Arrays.stream(WarehouseAttachmentType.values())
                            .map(WarehouseAttachmentType::name)
                            .collect(Collectors.joining(", ")));
        }
        return new Partial(types);
    }

    private static WarehouseAttachmentType parseType(String name) {
        try {
            return WarehouseAttachmentType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的附件类型: " + name, e);
        }
    }
}
