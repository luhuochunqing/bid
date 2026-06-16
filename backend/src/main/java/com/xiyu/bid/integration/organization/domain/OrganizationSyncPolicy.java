package com.xiyu.bid.integration.organization.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class OrganizationSyncPolicy {

    private static final String STAFF = "staff";
    private static final String MANAGER = "manager";
    private static final String ADMIN = "admin";

    private OrganizationSyncPolicy() {
    }

    public static Optional<OrganizationEventType> topicFromEventTopic(String eventTopic) {
        return switch (blankToEmpty(eventTopic)) {
            case "BaseOssDept" -> Optional.of(OrganizationEventType.DEPARTMENT_NOTICE);
            case "BaseOssUser" -> Optional.of(OrganizationEventType.USER_NOTICE);
            case "BaseOssJob" -> Optional.of(OrganizationEventType.JOB_NOTICE);
            default -> Optional.empty();
        };
    }

    public static String idempotencyKey(OrganizationEventNotice notice) {
        return String.join("|",
                blankToEmpty(notice.eventSource()),
                notice.topic().topic(),
                blankToEmpty(notice.key()),
                blankToEmpty(notice.time())
        );
    }

    public static String mapRoleCode(String externalRoleCode, Set<String> adminRoleCodes, Set<String> managerRoleCodes) {
        String normalized = normalize(externalRoleCode);
        if (adminRoleCodes.contains(normalized)) {
            return ADMIN;
        }
        if (managerRoleCodes.contains(normalized)) {
            return MANAGER;
        }
        return STAFF;
    }

    public static OrganizationUserSyncPlan planUserSync(
            OrganizationUserSnapshot incoming,
            String existingRoleCode,
            Set<String> adminRoleCodes,
            Set<String> managerRoleCodes
    ) {
        return planUserSync(incoming, existingRoleCode, adminRoleCodes, managerRoleCodes, null);
    }

    public static OrganizationUserSyncPlan planUserSync(
            OrganizationUserSnapshot incoming,
            String existingRoleCode,
            Set<String> adminRoleCodes,
            Set<String> managerRoleCodes,
            String positionMappedRoleCode
    ) {
        return planUserSync(incoming, existingRoleCode, adminRoleCodes, managerRoleCodes, positionMappedRoleCode, false);
    }

    public static OrganizationUserSyncPlan planUserSync(
            OrganizationUserSnapshot incoming,
            String existingRoleCode,
            Set<String> adminRoleCodes,
            Set<String> managerRoleCodes,
            String positionMappedRoleCode,
            boolean allowAdminElevation
    ) {
        String username = firstPresent(incoming.username(), incoming.externalUserId());
        String fullName = firstPresent(incoming.fullName(), username);
        String email = blankToEmpty(incoming.email());
        String roleCode = planRoleCode(incoming.externalRoleCode(), existingRoleCode, adminRoleCodes, managerRoleCodes, positionMappedRoleCode, allowAdminElevation);
        return new OrganizationUserSyncPlan(
                normalize(username),
                fullName.trim(),
                email.trim(),
                blankToEmpty(incoming.phone()),
                normalize(incoming.departmentCode()),
                blankToEmpty(incoming.departmentName()),
                roleCode,
                incoming.enabled(),
                false
        );
    }

    public static OrganizationDepartmentSyncPlan planDepartmentSync(OrganizationDepartmentSnapshot incoming) {
        String externalDeptId = blankToEmpty(incoming.externalDeptId());
        String departmentCode = normalize(firstPresent(incoming.departmentCode(), externalDeptId));
        return new OrganizationDepartmentSyncPlan(
                externalDeptId,
                departmentCode,
                firstPresent(incoming.departmentName(), departmentCode),
                blankToEmpty(incoming.parentExternalDeptId()),
                normalize(incoming.parentDepartmentCode()),
                incoming.enabled()
        );
    }

    private static String planRoleCode(
            String externalRoleCode,
            String existingRoleCode,
            Set<String> adminRoleCodes,
            Set<String> managerRoleCodes,
            String positionMappedRoleCode,
            boolean allowAdminElevation
    ) {
        String targetRole;
        if (positionMappedRoleCode != null && !positionMappedRoleCode.isBlank()) {
            targetRole = positionMappedRoleCode;
        } else {
            targetRole = mapRoleCode(externalRoleCode, adminRoleCodes, managerRoleCodes);
        }
        String existingRole = normalize(existingRoleCode);
        if (ADMIN.equals(targetRole) && !ADMIN.equals(existingRole) && !allowAdminElevation) {
            return STAFF.equals(existingRole) || MANAGER.equals(existingRole) ? existingRole : STAFF;
        }
        return targetRole;
    }

    private static String firstPresent(String preferred, String fallback) {
        return isBlank(preferred) ? blankToEmpty(fallback) : preferred;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return blankToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
