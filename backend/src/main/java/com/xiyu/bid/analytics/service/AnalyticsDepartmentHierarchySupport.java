// Input: 组织部门节点列表（code/parentCode/name）、已选部门名集合
// Output: 展开后代名集合（WHERE 匹配用）、叶子桶集合与分桶映射（图表分组用）
// Pos: Support/部门两级展示口径纯函数支持（无状态静态工具，禁框架依赖，可纯单测）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.ArrayList;
import java.util.Collection;
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
 * <p>三件事：</p>
 * <ol>
 *   <li><b>匹配展开</b>：勾选父部门时，统计范围扩展为其自身 ∪ 全部后代部门名
 *       （修复此前 {@code u.departmentName IN 选中集} 精确匹配漏掉挂二级人员数据的缺口）。</li>
 *   <li><b>展示桶</b>："选了一级只展示一级，只要有二级就展示二级"——无子部门的选中节点
 *       以自身为桶；有子部门的选中父节点不单独成柱，改为其直接子部门各成一桶；
 *       一次性选定整个下拉全选等场景同理逐棵子树展开。被选父节点仍直挂用户的罕见脏数据
 *       回退为自身名单独成桶，保证图上合计与筛选范围数目守恒。</li>
 * </ol>
 *
 * <p>嵌套选择防御：用户同时勾选父子两层时，更深（更具体）的选择优先；
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

    /**
     * 匹配展开：选中集合 → 选中 ∪ 全部后代 的部门名集合。
     *
     * @return null 表示未启用部门筛选（selectedNames 空），调用方保持既有"不过滤"语义
     */
    public static Set<String> expandWithDescendants(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return null;
        }
        Map<String, List<String>> childCodesByParent = indexChildrenByParentCode(nodes);
        Set<String> result = new LinkedHashSet<>();
        for (String selected : selectedNames) {
            for (DeptNode root : nodesByName(nodes).getOrDefault(selected, List.of())) {
                collectSubtreeNames(nodes, childCodesByParent, root.code(), new HashSet<>(), result);
            }
            // 组织树中不存在的选中名（兜底根/脏数据）：原样保留匹配语义
            result.add(selected);
        }
        return result;
    }

    /**
     * 展示桶集合：按"无子代选中节点自成桶、有子代选中节点由直接子部门成桶"规则展开。
     * 作为趋势图 expectedCategories（数量为 0 也展示，见 computeDimensionTrend）。
     * 嵌套选择时深层选择覆盖浅层结果；树外选中名（组织树兜底根）自成桶。
     */
    public static Set<String> displayBucketNames(List<DeptNode> nodes, Collection<String> selectedNames) {
        Map<String, String> covered = buildCoveredNameToBucket(nodes, selectedNames);
        Set<String> buckets = new LinkedHashSet<>(covered.values());
        // 树外兜底名不在覆盖映射内，但作为桶仍需展示（数量为 0 也展示）
        if (selectedNames != null) {
            for (String selected : selectedNames) {
                boolean inTree = nodesByName(nodes).containsKey(selected);
                if (!inTree && !covered.containsValue(selected)) {
                    buckets.add(selected);
                }
            }
        }
        return buckets;
    }

    /**
     * 分桶函数：用户部门名 → 图表分类标签。
     * 覆盖范围内的名称归入其桶；范围外（含被选父节点直挂用户）回退自身名。
     * 未启用筛选时返回恒等映射。
     */
    public static Function<String, String> bucketMapper(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return Function.identity();
        }
        Map<String, String> covered = buildCoveredNameToBucket(nodes, selectedNames);
        return (userName) -> covered.getOrDefault(userName, userName);
    }

    /**
     * 构建 用户部门名 → 展示桶标签 映射：
     * 每个选中的树内节点，无子代则整棵子树（自身）归入自身名；
     * 有子代则不为自己建桶（直挂用户回退自身名），改为每个直接子部门的子树归入该子部门名。
     * 嵌套选择先处理浅层再处理深层（深层条目后写入生效）。
     */
    private static Map<String, String> buildCoveredNameToBucket(List<DeptNode> nodes, Collection<String> selectedNames) {
        Map<String, List<DeptNode>> byName = nodesByName(nodes);
        Map<String, List<String>> childCodesByParent = indexChildrenByParentCode(nodes);
        List<String> ordered = selectedNames == null ? List.of()
                : selectedNames.stream()
                        .sorted((a, b) -> Integer.compare(minDepth(byName, a), minDepth(byName, b)))
                        .toList();
        Map<String, String> covered = new HashMap<>();
        for (String selected : ordered) {
            for (DeptNode node : byName.getOrDefault(selected, List.of())) {
                List<String> childCodes = childCodesByParent.getOrDefault(node.code(), List.of());
                if (childCodes.isEmpty()) {
                    covered.put(node.name(), node.name());
                    continue;
                }
                for (String childCode : childCodes) {
                    Set<String> subtreeNames = new LinkedHashSet<>();
                    collectSubtreeNames(nodes, childCodesByParent, childCode, new HashSet<>(), subtreeNames);
                    for (String name : subtreeNames) {
                        covered.put(name, name);
                    }
                }
            }
        }
        return covered;
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
            Map<String, List<String>> childCodesByParent,
            String code,
            Set<String> visited,
            Set<String> acc) {
        if (!visited.add(code)) {
            return;
        }
        nodes.stream()
                .filter(n -> n.code().equals(code))
                .filter(n -> n.name() != null && !n.name().isBlank())
                .findFirst()
                .ifPresent(n -> acc.add(n.name()));
        for (String childCode : childCodesByParent.getOrDefault(code, List.of())) {
            collectSubtreeNames(nodes, childCodesByParent, childCode, visited, acc);
        }
    }
}
