package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 任务批量分配规则 (Pure Core — no Spring dependencies)
 * 部门访问断言逻辑通过 supplier 函数注入，避免 core 依赖 application/service 层。
 */
public final class BatchAssignmentPolicy {

    private BatchAssignmentPolicy() {}

    public static BatchAssignmentSnapshot resolveDepartmentAssignment(
            TaskAssignmentRequest request, User currentUser,
            BiFunction<User, String, List<String>> deptCodesSupplier) {
        if (request == null || !request.hasAssignmentTarget()) {
            throw new IllegalArgumentException("Assignment target cannot be empty");
        }
        assertDeptAccess(currentUser, request.getAssigneeDeptCode(),
                Boolean.TRUE.equals(request.getAllowCrossDeptCollaboration()), deptCodesSupplier);
        return new BatchAssignmentSnapshot(
                null,
                normalizeText(request.getAssigneeDeptCode()),
                normalizeText(request.getAssigneeDeptName(), "未配置部门"),
                normalizeText(request.getAssigneeRoleCode()),
                normalizeText(request.getAssigneeRoleName())
        );
    }

    public static BatchAssignmentSnapshot resolveUserAssignment(User assignee, User currentUser,
                                                                boolean allowCrossDeptCollaboration,
                                                                BiFunction<User, String, List<String>> deptCodesSupplier) {
        if (!Boolean.TRUE.equals(assignee.getEnabled())) {
            throw new IllegalArgumentException("目标责任人已停用，无法分配");
        }
        assertDeptAccess(currentUser, assignee.getDepartmentCode(), allowCrossDeptCollaboration, deptCodesSupplier);
        return BatchAssignmentSnapshot.fromUser(assignee);
    }

    private static void assertDeptAccess(User currentUser, String targetDeptCode, boolean allowCrossDeptCollaboration,
                                          BiFunction<User, String, List<String>> deptCodesSupplier) {
        if (currentUser == null || isAdmin(currentUser)) {
            return;
        }
        List<String> allowedDeptCodes = new ArrayList<>(deptCodesSupplier.apply(currentUser, "READ"));
        if (currentUser.getDepartmentCode() != null && !currentUser.getDepartmentCode().isBlank()) {
            allowedDeptCodes.add(currentUser.getDepartmentCode().trim());
        }
        String normalizedTargetDept = normalizeText(targetDeptCode);
        if (normalizedTargetDept == null || allowedDeptCodes.isEmpty()) {
            return;
        }
        if (!allowedDeptCodes.contains(normalizedTargetDept)) {
            throw new IllegalArgumentException(allowCrossDeptCollaboration
                    ? "跨部门协作不在当前数据权限范围内"
                    : "当前用户无权向该部门分配任务");
        }
    }

    /** 纯核心推荐：返回验证错误而非抛出异常 */
    public static java.util.Optional<String> validateAssignmentRequest(
            TaskAssignmentRequest request, User currentUser,
            BiFunction<User, String, List<String>> deptCodesSupplier) {
        if (request == null || !request.hasAssignmentTarget()) {
            return java.util.Optional.of("Assignment target cannot be empty");
        }
        return validateDeptAccess(currentUser, request.getAssigneeDeptCode(),
                Boolean.TRUE.equals(request.getAllowCrossDeptCollaboration()), deptCodesSupplier);
    }

    private static java.util.Optional<String> validateDeptAccess(User currentUser, String targetDeptCode,
                                                                  boolean allowCrossDeptCollaboration,
                                                                  BiFunction<User, String, List<String>> deptCodesSupplier) {
        if (currentUser == null || isAdmin(currentUser)) {
            return java.util.Optional.empty();
        }
        List<String> allowedDeptCodes = new ArrayList<>(deptCodesSupplier.apply(currentUser, "READ"));
        if (currentUser.getDepartmentCode() != null && !currentUser.getDepartmentCode().isBlank()) {
            allowedDeptCodes.add(currentUser.getDepartmentCode().trim());
        }
        String normalizedTargetDept = normalizeText(targetDeptCode);
        if (normalizedTargetDept == null || allowedDeptCodes.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (!allowedDeptCodes.contains(normalizedTargetDept)) {
            return java.util.Optional.of(allowCrossDeptCollaboration
                    ? "跨部门协作不在当前数据权限范围内"
                    : "当前用户无权向该部门分配任务");
        }
        return java.util.Optional.empty();
    }

    private static boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRoleCode());
    }

    private static String normalizeText(String value) {
        return normalizeText(value, null);
    }

    private static String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
