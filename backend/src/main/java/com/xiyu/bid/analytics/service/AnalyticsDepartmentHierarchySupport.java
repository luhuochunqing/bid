// Input: 组织部门节点（code/parent/name）与勾选部门名
// Output: expand=勾选∪子孙（null=未启用）；bucketNames/toBucket 仅部门轴两级成柱
// Pos: Support/部门两级统计口径纯函数（无状态、零框架依赖）
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
 * 数据分析部门两级口径：勾父含后代；部门轴有二级则按直接子部门成柱，三级折进二级。
 * UI 仍为 check-strictly，本类只改统计口径。
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
     * 勾选 ∪ 全部子孙部门名。{@code selectedNames} 空/null 返回 null（调用方按"不过滤"处理）。
     */
    public static List<String> expand(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return null;
        }
        Index idx = Index.of(nodes);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String selected : selectedNames) {
            names.add(selected);
            for (DeptNode node : idx.byName().getOrDefault(selected, List.of())) {
                collectSubtree(idx, node, new HashSet<>(), names);
            }
        }
        return List.copyOf(names);
    }

    /**
     * 部门轴展示柱：无子部门的勾选自成柱；有直接子部门则子部门各成一柱（一级不保留汇总柱）。
     */
    public static List<String> bucketNames(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return List.of();
        }
        Index idx = Index.of(nodes);
        LinkedHashSet<String> buckets = new LinkedHashSet<>();
        for (String selected : selectedNames) {
            List<DeptNode> matches = idx.byName().getOrDefault(selected, List.of());
            if (matches.isEmpty()) {
                buckets.add(selected);
                continue;
            }
            for (DeptNode node : matches) {
                List<DeptNode> children = idx.childrenOf(node);
                if (children.isEmpty()) {
                    buckets.add(node.name());
                    continue;
                }
                for (DeptNode child : children) {
                    buckets.add(child.name());
                }
            }
        }
        return List.copyOf(buckets);
    }

    /**
     * 用户部门名 → 展示柱标签。挂在被选一级之直接子部门子树内的（含三级）归到该二级柱；其余恒等。
     */
    public static String toBucket(List<DeptNode> nodes, Collection<String> selectedNames, String userName) {
        if (userName == null || selectedNames == null || selectedNames.isEmpty()) {
            return userName;
        }
        Index idx = Index.of(nodes);
        for (String selected : selectedNames) {
            for (DeptNode node : idx.byName().getOrDefault(selected, List.of())) {
                List<DeptNode> children = idx.childrenOf(node);
                if (children.isEmpty()) {
                    continue;
                }
                for (DeptNode child : children) {
                    LinkedHashSet<String> subtree = new LinkedHashSet<>();
                    collectSubtree(idx, child, new HashSet<>(), subtree);
                    if (subtree.contains(userName)) {
                        return child.name();
                    }
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
    private record Index(Map<String, List<DeptNode>> byName, Map<String, List<DeptNode>> childrenByCode) {

        static Index of(List<DeptNode> nodes) {
            Map<String, List<DeptNode>> byName = new HashMap<>();
            Map<String, List<DeptNode>> children = new HashMap<>();
            if (nodes == null) {
                return new Index(byName, children);
            }
            for (DeptNode node : nodes) {
                byName.computeIfAbsent(node.name(), k -> new ArrayList<>()).add(node);
                if (node.parentCode() != null && !node.parentCode().isBlank()) {
                    children.computeIfAbsent(node.parentCode(), k -> new ArrayList<>()).add(node);
                }
            }
            return new Index(byName, children);
        }

        List<DeptNode> childrenOf(DeptNode node) {
            return childrenByCode.getOrDefault(node.code(), List.of());
        }
    }
}
