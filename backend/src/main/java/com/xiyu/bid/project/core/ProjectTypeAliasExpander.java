// Input: 用户筛选输入的项目类型列表（可能为标准枚举名/中文别名/历史值）
// Output: 扩展后的所有可能数据库存储值集合
// Pos: project/core/ - 纯规则，无 Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目类型别名扩展器（纯规则，无 Spring/JPA）。
 *
 * <p>从 InitiationFieldPolicy 抽取，遵循 Split-First Rule 单一职责原则。
 * 用于筛选场景：把用户选中的标准枚举名扩展为所有可能的数据库存储值
 * （中文/旧枚举名/标准名），以兼容历史数据。
 *
 * <p>示例：输入 "COLLECTIVE" → 返回 {"COLLECTIVE", "集采", "GROUP_PURCHASE"}
 */
public final class ProjectTypeAliasExpander {

    /** 标准 ProjectType 枚举名 → 所有可能的数据库存储值（中文/旧枚举名/标准名）。 */
    private static final Map<String, Set<String>> ALIASES_BY_STANDARD = buildAliases();

    private static Map<String, Set<String>> buildAliases() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : InitiationFieldPolicy.PROJECT_TYPE_MAPPING.entrySet()) {
            m.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
        // 标准名本身也要包含
        for (String std : new String[]{"OFFICE", "COMPREHENSIVE", "COLLECTIVE", "INDUSTRIAL", "OTHER"}) {
            m.computeIfAbsent(std, k -> new HashSet<>()).add(std);
        }
        // 转为不可变 Set
        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : m.entrySet()) {
            immutable.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private ProjectTypeAliasExpander() {}

    /**
     * 扩展项目类型值为所有可能的数据库存储别名，用于筛选时兼容历史数据。
     * 输入标准枚举名或别名，返回该类型所有可能的存储值。
     */
    public static Set<String> expand(List<String> projectTypes) {
        if (projectTypes == null || projectTypes.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String t : projectTypes) {
            String normalized = InitiationFieldPolicy.normalizeProjectType(t);
            if (normalized != null) {
                result.addAll(ALIASES_BY_STANDARD.getOrDefault(normalized, Set.of()));
            } else {
                result.add(t);
            }
        }
        return result;
    }
}
