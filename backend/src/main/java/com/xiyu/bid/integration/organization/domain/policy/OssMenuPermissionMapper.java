package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 将 OSS 菜单树节点映射为平台内部菜单权限码的纯函数策略。
 *
 * <p>映射规则：
 * <ol>
 *   <li>递归遍历所有节点（含 children）</li>
 *   <li>对 OSS menuCode 规范化：trim + 转小写</li>
 *   <li>优先使用配置映射表查找内部权限码（大小写不敏感）</li>
 *   <li>未命中时按默认行为处理：IGNORE 忽略，USE_NORMALIZED_CODE 使用规范化编码</li>
 * </ol>
 */
public class OssMenuPermissionMapper {

    private final Map<String, String> codeMappings;
    private final UnmappedBehavior unmappedBehavior;

    public OssMenuPermissionMapper(Map<String, String> codeMappings, String unmappedBehavior) {
        this.codeMappings = normalizeKeys(codeMappings);
        this.unmappedBehavior = UnmappedBehavior.from(unmappedBehavior);
    }

    /**
     * 将 OSS 菜单树映射为内部权限码集合。
     *
     * @param menuTree 根节点列表
     * @return 去重后的内部权限码集合
     */
    public Set<String> map(List<OssMenuTreeNode> menuTree) {
        Set<String> permissions = new HashSet<>();
        if (menuTree == null || menuTree.isEmpty()) {
            return permissions;
        }
        List<OssMenuTreeNode> stack = new ArrayList<>(menuTree);
        while (!stack.isEmpty()) {
            OssMenuTreeNode node = stack.removeLast();
            if (node == null) {
                continue;
            }
            mapNode(node).ifPresent(permissions::add);
            if (node.children() != null && !node.children().isEmpty()) {
                stack.addAll(node.children());
            }
        }
        return Set.copyOf(permissions);
    }

    private Optional<String> mapNode(OssMenuTreeNode node) {
        String normalized = node.normalizedMenuCode();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        String mapped = codeMappings.get(normalized);
        if (mapped != null && !mapped.isBlank()) {
            return Optional.of(mapped.trim());
        }
        if (unmappedBehavior == UnmappedBehavior.USE_NORMALIZED_CODE) {
            return Optional.of(normalized);
        }
        return Optional.empty();
    }

    private static Map<String, String> normalizeKeys(Map<String, String> mappings) {
        if (mappings == null) {
            return Map.of();
        }
        Map<String, String> normalized = new java.util.HashMap<>();
        mappings.forEach((key, value) -> {
            if (key != null) {
                normalized.put(key.trim().toLowerCase(Locale.ROOT), value == null ? "" : value.trim());
            }
        });
        return Map.copyOf(normalized);
    }

    private enum UnmappedBehavior {
        IGNORE,
        USE_NORMALIZED_CODE;

        static UnmappedBehavior from(String value) {
            if (value == null) {
                return IGNORE;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "use_normalized_code" -> USE_NORMALIZED_CODE;
                default -> IGNORE;
            };
        }
    }
}
