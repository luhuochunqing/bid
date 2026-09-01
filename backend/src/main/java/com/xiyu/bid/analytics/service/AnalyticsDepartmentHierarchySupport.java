// Input: 组织部门节点（code/parent/name）与勾选部门名
// Output: expand=有效勾选∪子孙；bucketNames/toBucket=按显示层级（锚定0001三级/回退全树两级）；
//         只勾某层按该层成柱、可见下级勾满折叠上级、部分勾选忽略上级保留下级
// Pos: Support/部门筛选与 X 轴口径纯函数（无状态、零框架依赖）
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

/**
 * 数据分析部门口径：与展示树（FilterOptionsQueryService.fetchDepartmentTree）对齐。
 * 锚点命中（西域集团 0001 存在且有子节点）→ 显示三级（一级=锚点直接子节点，其下两级）；
 * 锚点未命中 → 回退全树两级。按显示层级成柱：只勾某层按该层（子孙折进）；
 * 可见下级勾满折叠上级；部分勾选忽略上级保留下级。
 */
public final class AnalyticsDepartmentHierarchySupport {

    private AnalyticsDepartmentHierarchySupport() {
    }

    /** 组织部门最小视图；name 作筛选/柱标签。脏行请用 {@link #nodeOrNull} 跳过。 */
    public record DeptNode(String code, String parentCode, String name) {
        public DeptNode {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("departmentCode must not be blank");
            }
        }
    }

    /** code 或 name 为空时返回 null，避免脏行把整条筛选链路打成 500。 */
    public static DeptNode nodeOrNull(String code, String parentCode, String name) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            return null;
        }
        return new DeptNode(code, parentCode, name);
    }

    /**
     * 有效勾选 ∪ 全部子孙部门名。{@code selectedNames} 空/null 返回 null（调用方按"不过滤"处理）；
     * 勾选全部落在显示截断之外时返回空列表（调用方按"零命中"处理，禁止当"不过滤"）。有效勾选见 {@link #bucketNames}。
     */
    public static List<String> expand(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return null;
        }
        Index idx = Index.of(nodes);
        List<String> axis = idx.axisNames(selectedNames);
        if (axis.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String selected : axis) {
            names.add(selected);
            for (DeptNode node : idx.byName().getOrDefault(selected, List.of())) {
                collectSubtree(idx, node, new HashSet<>(), names);
            }
        }
        return List.copyOf(names);
    }

    /**
     * 部门轴展示柱 = 有效勾选：按显示层级（锚定三级/回退两级）成柱；
     * 可见下级勾满折叠上级；部分勾选忽略上级保留下级。
     */
    public static List<String> bucketNames(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return List.of();
        }
        return Index.of(nodes).axisNames(selectedNames);
    }

    /**
     * 用户部门名 → 展示柱标签。落在某展示柱子树内（含自身）归到该柱；其余恒等。
     */
    public static String toBucket(List<DeptNode> nodes, Collection<String> selectedNames, String userName) {
        if (userName == null) {
            return null;
        }
        Index idx = Index.of(nodes);
        List<String> axis = idx.axisNames(selectedNames);
        if (axis.isEmpty()) {
            return userName;
        }
        for (String axisName : axis) {
            for (DeptNode node : idx.byName().getOrDefault(axisName, List.of())) {
                LinkedHashSet<String> subtree = new LinkedHashSet<>();
                collectSubtree(idx, node, new HashSet<>(), subtree);
                if (subtree.contains(userName)) {
                    return axisName;
                }
            }
        }
        return userName;
    }

    private static void collectSubtree(Index idx, DeptNode node, Set<String> visited, Set<String> acc) {
        if (!visited.add(node.code())) {
            return;
        }
        acc.add(node.name());
        for (DeptNode child : idx.childrenOf(node)) {
            collectSubtree(idx, child, visited, acc);
        }
    }

    /** 按 code 建父子索引；成环脏数据由 visited 兜住。同名跨源不在本口径处理。 */
    private record Index(
            Map<String, List<DeptNode>> byName,
            Map<String, DeptNode> byCode,
            Map<String, List<DeptNode>> childrenByCode,
            boolean anchored) {

        static Index of(List<DeptNode> nodes) {
            Map<String, List<DeptNode>> byName = new HashMap<>();
            Map<String, DeptNode> byCode = new HashMap<>();
            Map<String, List<DeptNode>> children = new HashMap<>();
            if (nodes == null) {
                return new Index(byName, byCode, children, false);
            }
            for (DeptNode node : nodes) {
                byName.computeIfAbsent(node.name(), k -> new ArrayList<>()).add(node);
                byCode.put(node.code(), node);
                if (node.parentCode() != null && !node.parentCode().isBlank()) {
                    children.computeIfAbsent(node.parentCode(), k -> new ArrayList<>()).add(node);
                }
            }
            DeptNode anchor = byCode.get(FilterDepartmentTreeAnchorPolicy.ANCHOR_CODE);
            boolean anchored = anchor != null && !children.getOrDefault(anchor.code(), List.of()).isEmpty();
            return new Index(byName, byCode, children, anchored);
        }

        List<DeptNode> childrenOf(DeptNode node) {
            return childrenByCode.getOrDefault(node.code(), List.of());
        }

        /**
         * 展示轴：按显示层级分组后自底向上折叠——可见下级全勾折叠上级；勾了上级但只勾部分下级则忽略上级，保留实际勾选的下级。
         */
        List<String> axisNames(Collection<String> selectedNames) {
            if (selectedNames == null || selectedNames.isEmpty()) {
                return List.of();
            }
            int maxLevel = anchored ? 3 : 2;
            LinkedHashSet<String> level1 = new LinkedHashSet<>();
            LinkedHashSet<String> level2 = new LinkedHashSet<>();
            LinkedHashSet<String> level3 = new LinkedHashSet<>();
            for (String selected : selectedNames) {
                int level = selectionLevel(selected);
                if (level == 1) {
                    level1.add(selected);
                } else if (level == 2) {
                    level2.add(selected);
                } else if (level == 3 && maxLevel == 3) {
                    level3.add(selected);
                }
                // 超出显示截断的层级不入轴，与展示树对齐
            }
            if (level2.isEmpty() && level3.isEmpty()) {
                return List.copyOf(level1);
            }
            if (level3.isEmpty()) {
                // 两级口径：一级完整（二级勾满）折叠，其余保留二级
                LinkedHashSet<String> completeL1 = completeUpper(level1, level2, 2);
                return merge(completeL1, minus(level2, coveredChildren(completeL1, 2)));
            }
            // 三级口径：先处理二级——三级勾满折叠/部分勾选忽略二级，再据此判定一级完整性
            LinkedHashSet<String> keptL2 = new LinkedHashSet<>();
            Set<String> foldedL3 = new HashSet<>();
            for (String l2 : level2) {
                Set<String> l3Children = displayChildNames(l2, 3);
                Set<String> selectedBelow = new HashSet<>(l3Children);
                selectedBelow.retainAll(level3);
                if (selectedBelow.isEmpty() || selectedBelow.containsAll(l3Children)) {
                    keptL2.add(l2);
                    foldedL3.addAll(selectedBelow);
                }
            }
            LinkedHashSet<String> completeL1 = completeUpper(level1, keptL2, 2);
            LinkedHashSet<String> result = new LinkedHashSet<>(completeL1);
            result.addAll(minus(keptL2, coveredChildren(completeL1, 2)));
            for (String l3 : level3) {
                if (!foldedL3.contains(l3)) {
                    result.add(l3);
                }
            }
            return List.copyOf(result);
        }

        /** 下级全勾（或无下级）的上级视为完整：完整则折叠按上级成柱。 */
        private LinkedHashSet<String> completeUpper(LinkedHashSet<String> uppers, Set<String> lowers, int childLevel) {
            LinkedHashSet<String> complete = new LinkedHashSet<>();
            for (String upper : uppers) {
                Set<String> childNames = displayChildNames(upper, childLevel);
                if (childNames.isEmpty() || lowers.containsAll(childNames)) {
                    complete.add(upper);
                }
            }
            return complete;
        }

        /** 完整上级覆盖的下级名集合（用于剔除已折叠进上级的下级）。 */
        private Set<String> coveredChildren(Set<String> uppers, int childLevel) {
            Set<String> covered = new HashSet<>();
            for (String upper : uppers) {
                covered.addAll(displayChildNames(upper, childLevel));
            }
            return covered;
        }

        /** 完整上级在前、剩余下级在后（保持勾选顺序）。 */
        private static List<String> merge(Set<String> completeUpper, Set<String> leftover) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(completeUpper);
            merged.addAll(leftover);
            return List.copyOf(merged);
        }

        private static Set<String> minus(Set<String> all, Set<String> removed) {
            LinkedHashSet<String> leftover = new LinkedHashSet<>(all);
            leftover.removeAll(removed);
            return leftover;
        }

        /** 某名下（同名跨分支合并）指定显示层级的子节点名集合。 */
        Set<String> displayChildNames(String parentName, int childLevel) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (DeptNode node : byName.getOrDefault(parentName, List.of())) {
                for (DeptNode child : childrenOf(node)) {
                    if (displayLevelOf(child) == childLevel) {
                        names.add(child.name());
                    }
                }
            }
            return names;
        }

        int selectionLevel(String name) {
            List<DeptNode> matches = byName.getOrDefault(name, List.of());
            if (matches.isEmpty()) {
                return 1;
            }
            int min = Integer.MAX_VALUE;
            for (DeptNode node : matches) {
                min = Math.min(min, displayLevelOf(node));
            }
            return min;
        }

        /**
         * 显示层级：锚定模式以锚点为基准（锚点自身 0、直接子节点一级），锚定子树外节点与回退模式按物理深度。成环/断链兜底回物理深度。
         */
        int displayLevelOf(DeptNode node) {
            if (!anchored) {
                return levelOf(node);
            }
            int depthBelowAnchor = 0;
            Set<String> seen = new HashSet<>();
            DeptNode current = node;
            while (current != null) {
                if (!seen.add(current.code())) {
                    return levelOf(node);
                }
                if (FilterDepartmentTreeAnchorPolicy.ANCHOR_CODE.equals(current.code())) {
                    return depthBelowAnchor;
                }
                if (current.parentCode() == null || current.parentCode().isBlank()) {
                    return levelOf(node);
                }
                depthBelowAnchor++;
                current = byCode.get(current.parentCode());
            }
            return levelOf(node);
        }

        int levelOf(DeptNode node) {
            int level = 1;
            Set<String> seen = new HashSet<>();
            DeptNode current = node;
            while (current.parentCode() != null && !current.parentCode().isBlank()) {
                if (!seen.add(current.code())) {
                    break;
                }
                DeptNode parent = byCode.get(current.parentCode());
                if (parent == null) {
                    break;
                }
                current = parent;
                level++;
            }
            return level;
        }
    }
}
