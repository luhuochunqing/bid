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
public class SettingsResponse {

    private SystemConfig systemConfig;
    private List<RoleSetting> roles;
    private List<DeptDataScopeSetting> deptDataScope;
    private List<ProjectGroupScopeSetting> projectGroupScope;
    private IntegrationConfig integrationConfig;
    private AiModelConfig aiModelConfig;
    private List<FlowMappingSetting> flowMappings;
    private List<ApiSetting> apiList;
    private RuntimePermissionProfile runtimePermissionProfile;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemConfig {
        private String sysName;
        private Integer depositWarnDays;
        private Integer qualWarnDays;
        private Boolean enableAI;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleSetting {
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
    public static class DeptDataScopeSetting {
        private String deptName;
        private String dataScope;
        private Boolean canViewOtherDepts;
        private List<String> allowedDepts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectGroupScopeSetting {
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
    public static class IntegrationConfig {
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
    public static class AiModelConfig {
        private String activeProvider;
        private List<AiProviderSetting> providers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiProviderSetting {
        private String providerCode;
        private String providerName;
        private Boolean enabled;
        private String baseUrl;
        private String model;
        private String encryptedApiKey;
        private String apiKeyMasked;
        private Boolean apiKeyConfigured;
        private String lastTestStatus;
        private String lastTestMessage;
        private Instant lastTestAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowMappingSetting {
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
    public static class ApiSetting {
        private String name;
        private String path;
        private String method;
        private String description;
        private String status;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuntimePermissionProfile {
        private String code;
        private List<String> menuPermissions;
        private String dataScope;
        private List<String> allowedProjects;
        private List<String> allowedDepts;
    }
}
