package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 OSS 菜单树节点映射为平台内部菜单权限码的纯函数策略。
 *
 * <p>映射规则：
 * <ol>
 *   <li>递归遍历所有节点（含 children）</li>
 *   <li>对 OSS menuCode 规范化：trim + 转小写</li>
 *   <li>优先使用配置映射表查找内部权限码列表（大小写不敏感）</li>
 *   <li>未命中时按默认行为处理：IGNORE 忽略，USE_NORMALIZED_CODE 使用规范化编码</li>
 * </ol>
 */
public class OssMenuPermissionMapper {

    private final Map<String, List<String>> codeMappings;
    private final UnmappedBehavior unmappedBehavior;

    public OssMenuPermissionMapper(Map<String, List<String>> codeMappings, String unmappedBehavior) {
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
        List<OssMenuTreeNode> nodes = OssMenuTreeNode.flatten(menuTree);
        for (OssMenuTreeNode node : nodes) {
            permissions.addAll(resolve(node.normalizedMenuCode()));
        }
        return Set.copyOf(permissions);
    }

    /**
     * 将 OSS 权限码列表（如 ["1001","100402"]）直接映射为内部权限码集合。
     *
     * @param codes OSS 返回的菜单权限码列表
     * @return 去重后的内部权限码集合
     */
    public Set<String> mapCodes(List<String> codes) {
        Set<String> permissions = new HashSet<>();
        if (codes == null || codes.isEmpty()) {
            return Set.copyOf(permissions);
        }
        for (String code : codes) {
            if (code == null) {
                continue;
            }
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            permissions.addAll(resolve(normalized));
        }
        return Set.copyOf(permissions);
    }

    /**
     * 将单个规范化后的 OSS 菜单码解析为内部权限码集合。
     */
    private Set<String> resolve(String normalizedCode) {
        if (normalizedCode.isBlank()) {
            return Set.of();
        }
        List<String> mapped = codeMappings.get(normalizedCode);
        if (mapped != null && !mapped.isEmpty()) {
            return Set.copyOf(mapped);
        }
        if (unmappedBehavior == UnmappedBehavior.USE_NORMALIZED_CODE) {
            return Set.of(normalizedCode);
        }
        return Set.of();
    }

    private static Map<String, List<String>> normalizeKeys(Map<String, List<String>> mappings) {
        if (mappings == null) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new HashMap<>();
        mappings.forEach((key, values) -> {
            if (key == null) {
                return;
            }
            List<String> cleaned = values == null ? List.of() : values.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .map(String::trim)
                    .toList();
            normalized.put(key.trim().toLowerCase(Locale.ROOT), cleaned);
        });
        return Collections.unmodifiableMap(normalized);
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
