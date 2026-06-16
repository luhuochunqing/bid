package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationJobSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class OrganizationDirectoryJsonMapper {
    OrganizationDepartmentSnapshot department(JsonNode root) {
        JsonNode node = payloadNode(root);
        String externalDeptId = firstText(node, "deptId", "departmentId", "id");
        // YAPI returns "code" as department code; fallback to "deptCode" or "deptId" for compatibility
        String departmentCode = firstText(node, "code", "deptCode", "departmentCode", "deptId");
        return new OrganizationDepartmentSnapshot(
                externalDeptId,
                departmentCode,
                firstText(node, "name", "deptName", "departmentName"),
                // YAPI uses "administrativeSuperiors"; actual API returns "parentId"
                firstText(node, "parentId", "administrativeSuperiors", "parentDeptId", "parentDepartmentId"),
                // source: YAPI department response does not include parent department code
                firstText(node, "parentCode", "parentDeptCode", "parentDepartmentCode"),
                enabled(node)
        );
    }

    OrganizationUserSnapshot user(JsonNode root) {
        JsonNode node = payloadNode(root);
        String externalUserId = firstText(node, "userId", "id");
        // confirmed: YAPI returns "jobNumber"; tech spec confirms jobNumber = OA 账号 = username
        String username = firstText(node, "jobNumber", "userNo", "username", "loginName", "employeeNo", "userId");
        // YAPI returns "name" as full name
        String fullName = firstText(node, "name", "userName", "fullName");
        // YAPI returns "mobilePhone" for phone
        String phone = firstText(node, "mobilePhone", "mobile", "phone", "telephone");
        String jobId = firstText(node, "positionId", "jobId", "id");
        return new OrganizationUserSnapshot(
                externalUserId,
                username,
                fullName,
                firstText(node, "email", "mail"),
                phone,
                firstText(node, "deptCode", "departmentCode", "deptId"),
                firstText(node, "deptName", "departmentName"),
                jobId,
                firstText(node, "roleCode", "positionCode", "jobCode", "positionName", "jobName"),
                enabled(node)
        );
    }

    OrganizationJobSnapshot job(JsonNode root) {
        JsonNode node = payloadNode(root);
        String externalJobId = firstText(node, "jobId", "id");
        String jobName = firstText(node, "name", "jobName", "positionName");
        String jobCode = firstText(node, "code", "jobCode", "positionCode");
        return new OrganizationJobSnapshot(
                externalJobId,
                jobCode,
                jobName,
                enabled(node)
        );
    }

    List<OrganizationDepartmentSnapshot> departments(JsonNode root) {
        return snapshotNodes(root).stream().map(this::department).toList();
    }

    List<OrganizationUserSnapshot> users(JsonNode root) {
        return snapshotNodes(root).stream().map(this::user).toList();
    }

    private List<JsonNode> snapshotNodes(JsonNode root) {
        JsonNode payload = payloadNode(root);
        JsonNode array = payload.isArray()
                ? payload
                : firstArray(payload, "records", "items", "list", "departments", "users");
        if (array == null) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        return nodes;
    }

    private JsonNode payloadNode(JsonNode root) {
        if (root.has("data") && (root.get("data").isObject() || root.get("data").isArray())) {
            return root.get("data");
        }
        if (root.has("result") && (root.get("result").isObject() || root.get("result").isArray())) {
            return root.get("result");
        }
        return root;
    }

    private JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.isNull()) {
                String text = value.asText().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private boolean enabled(JsonNode node) {
        // confirmed: del=0=active, del=1=deleted. status=1 and activationState=1 = enabled.
        // Request already sends del=0&state=0, so server filters; this is safety net.
        JsonNode del = node.path("del");
        if (del.isInt() && del.asInt() == 1) {
            return false;
        }
        JsonNode status = node.path("status");
        if (status.isInt()) {
            return status.asInt() == 1;
        }
        JsonNode activationState = node.path("activationState");
        if (activationState.isInt()) {
            return activationState.asInt() == 1;
        }
        // Fallback to boolean fields (for compatibility with other formats)
        JsonNode enabled = node.path("enabled");
        if (enabled.isBoolean()) {
            return enabled.asBoolean();
        }
        JsonNode disabled = node.path("disabled");
        if (disabled.isBoolean()) {
            return !disabled.asBoolean();
        }
        String statusText = firstText(node, "status", "userStatus", "deptStatus").toLowerCase(Locale.ROOT);
        return !statusText.contains("disabled") && !statusText.contains("inactive")
                && !statusText.contains("停用") && !statusText.contains("离职");
    }
}
