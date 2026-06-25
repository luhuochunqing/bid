// Input: 候选人列表 + 权限/部门/角色过滤条件
// Output: 过滤并排序后的 AssignmentCandidateDTO 列表
// Pos: Core/纯核心（无 Spring 依赖）
// 维护声明: 纯过滤与排序逻辑，不依赖框架 / IO / 状态；AssignmentCandidateAppService 据此编排调用链。
package com.xiyu.bid.user.core;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分配候选人过滤策略（Pure Core — no Spring dependencies）。
 *
 * <p>负责候选人过滤与排序：
 * <ul>
 *   <li>hasGlobalAccess=true 时不过滤部门；false 时仅保留 allowedDeptCodes 内的候选人</li>
 *   <li>deptCode / roleCode 参数大小写不敏感过滤</li>
 *   <li>排序：departmentCode → roleName → fullName（nullsLast, CASE_INSENSITIVE）</li>
 * </ul>
 * 不依赖任何框架或 IO，所有输入由调用方注入。
 */
public class AssignmentCandidatePolicy {

    /**
     * 过滤并排序候选人。
     *
     * @param candidates        全部启用用户（由调用方注入）
     * @param hasGlobalAccess    当前用户是否拥有全局数据权限
     * @param allowedDeptCodes   当前用户可见部门列表（hasGlobalAccess=false 时生效）
     * @param context            业务场景上下文（P1.2: 用于场景化角色排除）
     * @param deptCode           可选部门过滤参数（大小写不敏感）
     * @param roleCode           可选角色过滤参数（大小写不敏感）
     * @return 过滤并排序后的候选人 DTO 列表
     */
    public List<AssignmentCandidateDTO> filter(
            List<User> candidates,
            boolean hasGlobalAccess,
            List<String> allowedDeptCodes,
            AssignmentContext context,
            String deptCode,
            String roleCode) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String normalizedDeptCode = trimToNull(deptCode);
        String normalizedRoleCode = trimToNull(roleCode);
        // P1.1: 将 List 转为 Set，O(n) 查找降为 O(1)
        Set<String> normalizedAllowed = allowedDeptCodes == null
                ? Set.of()
                : allowedDeptCodes.stream()
                        .filter(code -> code != null && !code.isBlank())
                        .map(String::toLowerCase)
                        .collect(Collectors.toCollection(HashSet::new));

        return candidates.stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> hasGlobalAccess || isVisibleDept(user, normalizedAllowed))
                .filter(user -> normalizedDeptCode == null
                        || normalizedDeptCode.equalsIgnoreCase(user.getDepartmentCode()))
                .filter(user -> normalizedRoleCode == null
                        || normalizedRoleCode.equalsIgnoreCase(user.getRoleCode()))
                // P1.2: 场景化角色排除——task 排除 staff，tender 排除 staff + admin_staff
                .filter(user -> context == null || !context.isExcludedRole(user.getRoleCode()))
                .sorted(Comparator
                        .comparing(User::getDepartmentCode,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(User::getRoleName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(User::getFullName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toDTO)
                .toList();
    }

    private AssignmentCandidateDTO toDTO(User user) {
        return new AssignmentCandidateDTO(
                user.getId(),
                user.getFullName(),
                user.getEmployeeNumber(),
                user.getRoleCode(),
                user.getRoleName(),
                user.getDepartmentCode(),
                user.getDepartmentName(),
                Boolean.TRUE.equals(user.getEnabled()));
    }

    private static boolean isVisibleDept(User user, Set<String> allowedDeptCodes) {
        if (allowedDeptCodes.isEmpty()) {
            return false;
        }
        String userDept = user.getDepartmentCode();
        if (userDept == null) {
            return false;
        }
        // P1.1: O(1) Set 查找替代 O(n) List stream
        return allowedDeptCodes.contains(userDept.toLowerCase());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
