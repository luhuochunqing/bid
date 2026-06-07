package com.xiyu.bid.admin.settings.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DepartmentGraphPolicy {
    public static final String UNASSIGNED_DEPT_CODE = "UNASSIGNED";
    public static final String UNASSIGNED_DEPT_NAME = "未分配";

    private DepartmentGraphPolicy() {
    }

    public static DepartmentGraph buildGraph(List<DepartmentNode> departments) {
        Map<String, DepartmentNode> definitions = new LinkedHashMap<>();
        normalizeTree(departments).forEach(node -> definitions.put(node.code(), node));
        if (definitions.isEmpty()) {
            definitions.put(UNASSIGNED_DEPT_CODE, new DepartmentNode(UNASSIGNED_DEPT_CODE, UNASSIGNED_DEPT_NAME, null, 0));
        }
        List<DepartmentNode> tree = definitions.values().stream()
                .sorted(Comparator.comparingInt(DepartmentNode::sortOrder).thenComparing(DepartmentNode::name))
                .toList();
        List<DepartmentOption> options = tree.stream()
                .map(node -> new DepartmentOption(node.code(), node.name()))
                .toList();
        return new DepartmentGraph(Map.copyOf(definitions), options, tree);
    }

    public static List<DepartmentNode> normalizeTree(List<DepartmentNode> departments) {
        if (departments == null) {
            return List.of();
        }
        return departments.stream()
                .filter(node -> hasText(node == null ? null : node.code()))
                .map(node -> new DepartmentNode(
                        normalizeCode(node.code()),
                        normalizeName(node.name()),
                        normalizeParent(node.parentCode()),
                        node.sortOrder()
                ))
                .distinct()
                .toList();
    }

    public static OrganizationValidationResult validateTree(List<DepartmentNode> departments) {
        List<DepartmentNode> normalized = normalizeTree(departments);
        if (normalized.stream().map(DepartmentNode::code).distinct().count() != normalized.size()) {
            return OrganizationValidationResult.invalid("部门编码不能重复");
        }
        for (DepartmentNode node : normalized) {
            if (!hasText(node.code()) || !hasText(node.name())) {
                return OrganizationValidationResult.invalid("部门编码和名称不能为空");
            }
            if (Objects.equals(node.code(), node.parentCode())) {
                return OrganizationValidationResult.invalid("上级部门不能指向自身");
            }
        }
        return hasCycle(normalized)
                ? OrganizationValidationResult.invalid("部门树不能形成循环")
                : OrganizationValidationResult.ok();
    }

    public static List<String> findRemovedBoundDepartments(List<DepartmentNode> nextTree, Set<String> assignedDepartmentCodes) {
        Set<String> nextCodes = normalizeTree(nextTree).stream()
                .map(DepartmentNode::code)
                .collect(java.util.stream.Collectors.toSet());
        return assignedDepartmentCodes.stream()
                .filter(DepartmentGraphPolicy::hasText)
                .map(DepartmentGraphPolicy::normalizeCode)
                .filter(code -> !nextCodes.contains(code))
                .sorted()
                .toList();
    }

    public static String normalizeCode(String code) {
        return hasText(code) ? code.trim() : UNASSIGNED_DEPT_CODE;
    }

    public static String normalizeName(String name) {
        return hasText(name) ? name.trim() : UNASSIGNED_DEPT_NAME;
    }

    private static boolean hasCycle(List<DepartmentNode> nodes) {
        Map<String, String> parentByCode = nodes.stream()
                .filter(node -> hasText(node.parentCode()))
                .collect(java.util.stream.Collectors.toMap(DepartmentNode::code, DepartmentNode::parentCode));
        for (String code : parentByCode.keySet()) {
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            String current = code;
            while (hasText(current)) {
                if (!seen.add(current)) {
                    return true;
                }
                current = parentByCode.get(current);
            }
        }
        return false;
    }

    private static String normalizeParent(String parentCode) {
        return hasText(parentCode) ? normalizeCode(parentCode) : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
