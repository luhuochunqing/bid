package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OSS 登录流程结果。
 * <p>
 * 包含 token、员工信息、权限等完整登录数据。
 */
@Getter
public class OssLoginResult {

    private final String username;
    private final boolean authenticated;
    private final String ossAccessToken;
    private final JsonNode employeeInfo;
    private final CrmUserPermission permission;
    private final CrmJobListResponse jobList;

    private OssLoginResult(Builder builder) {
        this.username = builder.username;
        this.authenticated = builder.authenticated;
        this.ossAccessToken = builder.ossAccessToken;
        this.employeeInfo = builder.employeeInfo;
        this.permission = builder.permission;
        this.jobList = builder.jobList;
    }

    public String username() {
        return username;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String ossAccessToken() {
        return ossAccessToken;
    }

    public JsonNode employeeInfo() {
        return employeeInfo;
    }

    public CrmUserPermission permission() {
        return permission;
    }

    public CrmJobListResponse jobList() {
        return jobList;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", username);
        map.put("authenticated", authenticated);
        if (employeeInfo != null) {
            map.put("employeeInfo", mapFromJson(employeeInfo));
        }
        if (permission != null) {
            map.put("permission", permission.systemPermissions());
        }
        return map;
    }

    private Object mapFromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(mapFromJson(item));
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                map.put(entry.getKey(), mapFromJson(entry.getValue()));
            });
            return map;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String username;
        private boolean authenticated;
        private String ossAccessToken;
        private JsonNode employeeInfo;
        private CrmUserPermission permission;
        private CrmJobListResponse jobList;

        public Builder username(String value) {
            this.username = value;
            return this;
        }

        public Builder authenticated(boolean value) {
            this.authenticated = value;
            return this;
        }

        public Builder ossAccessToken(String value) {
            this.ossAccessToken = value;
            return this;
        }

        public Builder employeeInfo(JsonNode value) {
            this.employeeInfo = value;
            return this;
        }

        public Builder permission(CrmUserPermission value) {
            this.permission = value;
            return this;
        }

        public Builder jobList(CrmJobListResponse value) {
            this.jobList = value;
            return this;
        }

        public OssLoginResult build() {
            return new OssLoginResult(this);
        }
    }
}
