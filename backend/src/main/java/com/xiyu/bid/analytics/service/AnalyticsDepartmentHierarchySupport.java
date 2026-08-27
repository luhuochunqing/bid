// Input: 组织部门节点列表（code/parentCode/name）、已选部门名集合
// Output: Prepared——匹配展开名集（WHERE 用）、展示桶集（图表 X 轴用）、组织名全集与分桶映射（下钻反解用）
// Pos: Support/部门两级展示口径纯函数支持（无状态静态工具，禁框架依赖，可纯单测）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 数据分析部门两级展示口径支持（业务规则："选了一级只展示一级，只要有二级就展示二级"）。
 *
 * <p>单一入口 {@link #prepare} 对给定组织树与选中部门名集合一次完成三件事，供调用方复用：</p>
 * <ol>
 *   <li><b>匹配展开</b>：勾选父部门时统计范围扩展为其自身 ∪ 全部后代部门名
 *       （修复此前 {@code u.departmentName IN 选中集} 精确匹配漏掉挂二级人员数据的缺口）。</li>
 *   <li><b>展示桶</b>：无子部门的选中节点以自身为桶；有子部门的选中父节点不单独成柱，
 *       由其直接子部门各成一桶；被选父节点仍直挂用户的罕见脏数据回退自身名成桶，
 *       保证图上合计与筛选范围数目守恒。</li>
 *   <li><b>分桶映射</b>：用户部门名 → 桶标签，图表分组与下钻反解共用同源口径。</li>
 * </ol>
 *
 * <p>嵌套选择（父子同时勾选）时更深的选择优先（深者后写生效）；树外兜底选中名自成桶。
 * 内部以 departmentCode 为键建父子索引，name 仅作输出标签。</p>
 */
public final class AnalyticsDepartmentHierarchySupport {

    private AnalyticsDepartmentHierarchySupport() {
    }

    /** 组织部门最小视图 record：调用方从 OrganizationDepartmentEntity 映射，保持本类零框架依赖。 */
    public record DeptNode(String code, String parentCode, String name) {

        public DeptNode {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("departmentCode must not be blank");
            }
        }
    }

    /** 预编译上下文：一次 {@link #prepare} 供展开/分桶/下钻反解复用。 */
    public record Prepared(
            Set<String> matchNames,
            Set<String> bucketNames,
            Set<String> allOrgNames,
            Function<String, String> bucketMapper) {

        /** 匹配名列表视图；未启用筛选时返回 null（保持"不过滤"语义）。 */
        public List<String> matchNamesList() {
            return matchNames == null ? null : List.copyOf(matchNames);
        }

        /** 是否启用了部门筛选选择。 */
        public boolean hasSelection() {
            return matchNames != null;
        }
    }

    /**
     * 构建两级口径上下文：一次遍历完成后代展开、展示桶与分桶映射。
     * 未启用筛选（selectedNames 空/null）时 matchNames=null、bucketMapper=恒等。
     */
    public static Prepared prepare(List<DeptNode> nodes, Collection<String> selectedNames) {
        Set<String> allOrgNames = collectAllNames(nodes);
        if (selectedNames == null || selectedNames.isEmpty()) {
            return new Prepared(null, Set.of(), allOrgNames, Function.identity());
        }
        Map<String, List<DeptNode>> byName = nodesByName(nodes);
        Map<String, List<String>> children = indexChildrenByParentCode(nodes);

        // 1. 匹配展开：选中 ∪ 全部后代名（组织树外选中名原样保留）
        Set<String> matchNames = new LinkedHashSet<>();
        for (String selected : selectedNames) {
            for (DeptNode root : byName.getOrDefault(selected, List.of())) {
                collectSubtreeNames(nodes, children, root.code(), new HashSet<>(), matchNames);
            }
            matchNames.add(selected);
        }

        // 2+3. 展示桶与分桶：按节点深度升序处理，深者后写生效（嵌套选择优先）。
        //   无子代选中节点自成桶；有子代选中节点由其直接子部门各成一桶并覆盖其整棵子树
        List<String> ordered = selectedNames.stream()
                .sorted(Comparator.comparingInt(n -> minDepth(byName, n)))
                .toList();
        Map<String, String> covered = new HashMap<>();
        Set<String> buckets = new LinkedHashSet<>();
        for (String selected : ordered) {
            for (DeptNode node : byName.getOrDefault(selected, List.of())) {
                List<String> childCodes = children.getOrDefault(node.code(), List.of());
                if (childCodes.isEmpty()) {
                    covered.put(node.name(), node.name());
                    buckets.add(node.name());
                    continue;
                }
                for (String childCode : childCodes) {
                    String childName = nameOf(nodes, childCode);
                    if (childName == null) {
                        continue;
                    }
                    Set<String> subtreeNames = new LinkedHashSet<>();
                    collectSubtreeNames(nodes, children, childCode, new HashSet<>(), subtreeNames);
                    for (String name : subtreeNames) {
                        covered.put(name, childName);
                    }
                    buckets.add(childName);
                }
            }
        }
        // 树外选中名不在覆盖映射内，自成桶（数量为 0 也展示）；树内父级不在此列
        for (String selected : selectedNames) {
            if (!byName.containsKey(selected) && !buckets.contains(selected)) {
                buckets.add(selected);
            }
        }
        return new Prepared(java.util.Collections.unmodifiableSet(matchNames),
                java.util.Collections.unmodifiableSet(buckets), allOrgNames,
                (userName) -> covered.getOrDefault(userName, userName));
    }

    private static Set<String> collectAllNames(List<DeptNode> nodes) {
        Set<String> names = new LinkedHashSet<>();
        for (DeptNode node : nodes) {
            if (node.name() != null && !node.name().isBlank()) {
                names.add(node.name());
            }
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    private static String nameOf(List<DeptNode> nodes, String code) {
        for (DeptNode node : nodes) {
            if (node.code().equals(code)) {
                return node.name();
            }
        }
        return null;
    }

    private static Map<String, List<DeptNode>> nodesByName(List<DeptNode> nodes) {
        Map<String, List<DeptNode>> byName = new HashMap<>();
        for (DeptNode node : nodes) {
            if (node.name() != null && !node.name().isBlank()) {
                byName.computeIfAbsent(node.name(), k -> new ArrayList<>()).add(node);
            }
        }
        return byName;
    }

    private static Map<String, List<String>> indexChildrenByParentCode(List<DeptNode> nodes) {
        Map<String, List<String>> children = new HashMap<>();
        for (DeptNode node : nodes) {
            if (node.parentCode() != null && !node.parentCode().isBlank()) {
                children.computeIfAbsent(node.parentCode(), k -> new ArrayList<>()).add(node.code());
            }
        }
        return children;
    }

    /** DFS 收集子树（含自身）全部部门名；visited 防御环状脏数据。 */
    private static void collectSubtreeNames(
            List<DeptNode> nodes,
            Map<String, List<String>> children,
            String code,
            Set<String> visited,
            Set<String> acc) {
        if (!visited.add(code)) {
            return;
        }
        String name = nameOf(nodes, code);
        if (name != null && !name.isBlank()) {
            acc.add(name);
        }
        for (String childCode : children.getOrDefault(code, List.of())) {
            collectSubtreeNames(nodes, children, childCode, visited, acc);
        }
    }

    /** 选中名对应全部实例的最小深度（根为 0），用于嵌套选择的深浅排序。 */
    private static int minDepth(Map<String, List<DeptNode>> byName, String name) {
        return byName.getOrDefault(name, List.of()).stream()
                .mapToInt(n -> depthOf(byName, n))
                .min()
                .orElse(0);
    }

    private static int depthOf(Map<String, List<DeptNode>> byName, DeptNode target) {
        int depth = 0;
        DeptNode current = target;
        Set<String> visited = new HashSet<>();
        while (current.parentCode() != null && !current.parentCode().isBlank() && visited.add(current.code())) {
            final String parentCode = current.parentCode();
            DeptNode parent = byName.values().stream()
                    .flatMap(List::stream)
                    .filter(n -> n.code().equals(parentCode))
                    .findFirst()
                    .orElse(null);
            if (parent == null) {
                break;
            }
            current = parent;
            depth++;
        }
        return depth;
    }
}
