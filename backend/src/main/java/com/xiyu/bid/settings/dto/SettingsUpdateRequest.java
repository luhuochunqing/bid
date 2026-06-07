package com.xiyu.bid.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsUpdateRequest {

    private SystemConfigUpdate systemConfig;
    private List<RoleSettingUpdate> roles;
    private List<DeptDataScopeUpdate> deptDataScope;
    private List<ProjectGroupScopeUpdate> projectGroupScope;
    private IntegrationConfigUpdate integrationConfig;
    private AiModelConfigUpdate aiModelConfig;
    private List<FlowMappingUpdate> flowMappings;
    private List<ApiSettingUpdate> apiList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemConfigUpdate {
        private String sysName;
        private Integer depositWarnDays;
        private Integer qualWarnDays;
        private Boolean enableAI;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleSettingUpdate {
        private String code;
        private String name;
        private String description;
        private Integer userCount;
        private String dataScope;
        private List<String> menuPermissions;
        private List<String> allowedProjects;
        private List<String> allowedDepts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeptDataScopeUpdate {
        private String deptName;
        private String dataScope;
        private Boolean canViewOtherDepts;
        private List<String> allowedDepts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectGroupScopeUpdate {
        private String groupName;
        private String manager;
        private Integer memberCount;
        private String visibility;
        private List<String> allowedRoles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationConfigUpdate {
        private Boolean orgEnabled;
        private String orgSystem;
        private String orgAppKey;
        private String orgAppSecret;
        private Boolean oaEnabled;
        private String oaUrl;
        private Boolean ssoEnabled;
        private String callbackUrl;
        private String apiKey;
        private String aiBaseUrl;
        private String aiModel;
        private String ipWhitelist;
        private String orgDirectoryBaseUrl;
        private String orgDirectoryAuthClientId;
        private String orgDirectoryAuthClientSecret;
        private String orgDirectoryUserDetailPath;
        private String orgDirectoryDeptDetailPath;
        private String orgDirectoryUserWindowPath;
        private String orgDirectoryDeptWindowPath;
        private Boolean orgEventSdkEnabled;
        private String orgEventConsumerGroup;
        private String orgEventServerRegisterUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiModelConfigUpdate {
        private String activeProvider;
        private List<AiProviderSettingUpdate> providers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiProviderSettingUpdate {
        private String providerCode;
        private Boolean enabled;
        private String baseUrl;
        private String model;
        private String apiKeyPlaintext;
        private String lastTestStatus;
        private String lastTestMessage;
        private Instant lastTestAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowMappingUpdate {
        private String systemFlow;
        private String oaFlow;
        private String oaFlowCode;
        private String oaFlowName;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiSettingUpdate {
        private String name;
        private String path;
        private String method;
        private String description;
        private String status;
        private Boolean enabled;
    }
}
