// Input: 组织部门节点（code/parent/name）与勾选部门名
// Output: expand=有效勾选∪子孙；bucketNames/toBucket=L1-only 按一级、L2-only 按二级、混选忽略不完整一级；一级下二级勾满则该一级完整
// Pos: Support/部门两级筛选与 X 轴口径纯函数（无状态、零框架依赖）
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
 * 数据分析部门两级口径：筛选树只到二级。
 * 只选一级 → X 轴按一级成柱（子孙折进一级）；只选二级 → 按二级成柱；
 * 一级+部分二级 → 忽略该一级；某一级的全部二级都勾了（全选/人员轴勾满）→ 该一级完整，按一级成柱。
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
     * 有效勾选 ∪ 全部子孙部门名。{@code selectedNames} 空/null 返回 null（调用方按"不过滤"处理）。
     * 有效勾选见 {@link #bucketNames}：不完整混选忽略一级；二级勾满的一级保留。
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
     * 部门轴展示柱 = 有效勾选：只选一级按一级；只选二级按二级；
     * 混选忽略未勾满二级的一级；一级下全部二级都在勾选里则该一级完整。
     */
    public static List<String> bucketNames(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return List.of();
        }
        return Index.of(nodes).axisNames(selectedNames);
    }

    /**
     * 用户部门名 → 展示柱标签。落在某展示柱子树内（含自身、含三级）归到该柱；其余恒等。
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
            Map<String, List<DeptNode>> childrenByCode) {

        static Index of(List<DeptNode> nodes) {
            Map<String, List<DeptNode>> byName = new HashMap<>();
            Map<String, DeptNode> byCode = new HashMap<>();
            Map<String, List<DeptNode>> children = new HashMap<>();
            if (nodes == null) {
                return new Index(byName, byCode, children);
            }
            for (DeptNode node : nodes) {
                byName.computeIfAbsent(node.name(), k -> new ArrayList<>()).add(node);
                byCode.put(node.code(), node);
                if (node.parentCode() != null && !node.parentCode().isBlank()) {
                    children.computeIfAbsent(node.parentCode(), k -> new ArrayList<>()).add(node);
                }
            }
            return new Index(byName, byCode, children);
        }

        List<DeptNode> childrenOf(DeptNode node) {
            return childrenByCode.getOrDefault(node.code(), List.of());
        }

        List<String> axisNames(Collection<String> selectedNames) {
            if (selectedNames == null || selectedNames.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> level1 = new LinkedHashSet<>();
            LinkedHashSet<String> level2 = new LinkedHashSet<>();
            for (String selected : selectedNames) {
                int level = selectionLevel(selected);
                if (level == 2) {
                    level2.add(selected);
                } else if (level == 1) {
                    level1.add(selected);
                }
            }
            if (level2.isEmpty()) {
                return List.copyOf(level1);
            }
            LinkedHashSet<String> completeL1 = new LinkedHashSet<>();
            Set<String> coveredL2 = new HashSet<>();
            for (String l1 : level1) {
                Set<String> childNames = l2ChildNames(l1);
                if (childNames.isEmpty() || level2.containsAll(childNames)) {
                    completeL1.add(l1);
                    coveredL2.addAll(childNames);
                }
            }
            LinkedHashSet<String> leftoverL2 = new LinkedHashSet<>(level2);
            leftoverL2.removeAll(coveredL2);
            if (leftoverL2.isEmpty()) {
                return List.copyOf(completeL1);
            }
            if (completeL1.isEmpty()) {
                return List.copyOf(leftoverL2);
            }
            completeL1.addAll(leftoverL2);
            return List.copyOf(completeL1);
        }

        Set<String> l2ChildNames(String l1Name) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (DeptNode node : byName.getOrDefault(l1Name, List.of())) {
                for (DeptNode child : childrenOf(node)) {
                    names.add(child.name());
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
                min = Math.min(min, levelOf(node));
            }
            return min;
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
