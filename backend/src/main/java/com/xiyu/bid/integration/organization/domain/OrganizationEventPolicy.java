package com.xiyu.bid.integration.organization.domain;

import java.util.Locale;
import java.util.Set;

public final class OrganizationEventPolicy {

    private OrganizationEventPolicy() {
    }

    public static OrganizationEventValidation validateEnvelope(
            OrganizationEventEnvelope envelope,
            Set<String> allowedSourceApps
    ) {
        if (isBlank(envelope.sourceApp()) || !allowedSourceApps.contains(envelope.sourceApp().trim())) {
            return OrganizationEventValidation.invalid("事件来源不在白名单内");
        }
        if (isBlank(envelope.traceId())) {
            return OrganizationEventValidation.invalid("事件缺少链路追踪编号");
        }
        if (isBlank(envelope.message())) {
            return OrganizationEventValidation.invalid("事件消息内容不能为空");
        }
        return OrganizationSyncPolicy.topicFromEventTopic(envelope.topic())
                .map(OrganizationEventValidation::ok)
                .orElseGet(() -> OrganizationEventValidation.invalid("不支持的组织事件主题"));
    }

    public static String mapRoleCode(String externalRoleCode, Set<String> adminRoleCodes, Set<String> managerRoleCodes) {
        return OrganizationSyncPolicy.mapRoleCode(externalRoleCode, adminRoleCodes, managerRoleCodes);
    }

    public static OrganizationUserSyncPlan planUserSync(
            OrganizationUserSnapshot incoming,
            Set<String> adminRoleCodes,
            Set<String> managerRoleCodes
    ) {
        return planUserSync(incoming, adminRoleCodes, managerRoleCodes, null);
    }

    public static OrganizationUserSyncPlan planUserSync(
            OrganizationUserSnapshot incoming,
            Set<String> adminRoleCodes,
            Set<String> managerRoleCodes,
            String positionMappedRoleCode
    ) {
        String username = firstPresent(incoming.username(), incoming.externalUserId());
        String fullName = firstPresent(incoming.fullName(), username);
        String email = firstPresent(incoming.email(), username + "@external-org.local");
        String roleCode = OrganizationSyncPolicy.planUserSync(
                incoming, null, adminRoleCodes, managerRoleCodes, positionMappedRoleCode
        ).roleCode();
        return new OrganizationUserSyncPlan(
                normalize(username),
                fullName.trim(),
                email.trim(),
                blankToEmpty(incoming.phone()),
                normalize(incoming.departmentCode()),
                blankToEmpty(incoming.departmentName()),
                roleCode,
                incoming.enabled()
        );
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
