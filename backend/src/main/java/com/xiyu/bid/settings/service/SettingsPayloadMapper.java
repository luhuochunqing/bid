package com.xiyu.bid.settings.service;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
class SettingsPayloadMapper {

    private final SettingsDefaultPayloadFactory defaultPayloadFactory;

    SettingsResponse createDefaultSettings() {
        return defaultPayloadFactory.createDefaultSettings();
    }

    SettingsResponse copy(SettingsResponse source) {
        return SettingsResponse.builder()
                .systemConfig(copySystemConfig(source.getSystemConfig()))
                .roles(copyRoles(source.getRoles()))
                .deptDataScope(copyDeptScopes(source.getDeptDataScope()))
                .projectGroupScope(copyProjectScopes(source.getProjectGroupScope()))
                .integrationConfig(copyIntegrationConfig(source.getIntegrationConfig()))
                .flowMappings(copyFlowMappings(source.getFlowMappings()))
                .apiList(copyApiSettings(source.getApiList()))
                .build();
    }

    SettingsResponse.SystemConfig copySystemConfig(SettingsResponse.SystemConfig source) {
        return SettingsResponse.SystemConfig.builder()
                .sysName(source.getSysName())
                .depositWarnDays(source.getDepositWarnDays())
                .qualWarnDays(source.getQualWarnDays())
                .enableAI(source.getEnableAI())
                .build();
    }

    SettingsResponse.SystemConfig copySystemConfigFromUpdate(SettingsUpdateRequest.SystemConfigUpdate source) {
        return SettingsResponse.SystemConfig.builder()
                .sysName(source.getSysName())
                .depositWarnDays(source.getDepositWarnDays())
                .qualWarnDays(source.getQualWarnDays())
                .enableAI(source.getEnableAI())
                .build();
    }

    SettingsResponse.IntegrationConfig copyIntegrationConfig(SettingsResponse.IntegrationConfig source) {
        return SettingsResponse.IntegrationConfig.builder()
                .orgEnabled(source.getOrgEnabled())
                .orgSystem(source.getOrgSystem())
                .orgAppKey(source.getOrgAppKey())
                .orgAppSecret(source.getOrgAppSecret())
                .oaEnabled(source.getOaEnabled())
                .oaUrl(source.getOaUrl())
                .ssoEnabled(source.getSsoEnabled())
                .callbackUrl(source.getCallbackUrl())
                .apiKey(source.getApiKey())
                .aiBaseUrl(source.getAiBaseUrl())
                .aiModel(source.getAiModel())
                .ipWhitelist(source.getIpWhitelist())
                .orgDirectoryBaseUrl(source.getOrgDirectoryBaseUrl())
                .orgDirectoryAuthClientId(source.getOrgDirectoryAuthClientId())
                .orgDirectoryAuthClientSecret(source.getOrgDirectoryAuthClientSecret())
                .orgDirectoryUserDetailPath(source.getOrgDirectoryUserDetailPath())
                .orgDirectoryDeptDetailPath(source.getOrgDirectoryDeptDetailPath())
                .orgDirectoryUserWindowPath(source.getOrgDirectoryUserWindowPath())
                .orgDirectoryDeptWindowPath(source.getOrgDirectoryDeptWindowPath())
                .orgEventSdkEnabled(source.getOrgEventSdkEnabled())
                .orgEventConsumerGroup(source.getOrgEventConsumerGroup())
                .orgEventServerRegisterUrl(source.getOrgEventServerRegisterUrl())
                .build();
    }

    SettingsResponse.IntegrationConfig copyIntegrationConfigFromUpdate(SettingsUpdateRequest.IntegrationConfigUpdate source) {
        return SettingsResponse.IntegrationConfig.builder()
                .orgEnabled(source.getOrgEnabled())
                .orgSystem(source.getOrgSystem())
                .orgAppKey(source.getOrgAppKey())
                .orgAppSecret(source.getOrgAppSecret())
                .oaEnabled(source.getOaEnabled())
                .oaUrl(source.getOaUrl())
                .ssoEnabled(source.getSsoEnabled())
                .callbackUrl(source.getCallbackUrl())
                .apiKey(source.getApiKey())
                .aiBaseUrl(source.getAiBaseUrl())
                .aiModel(source.getAiModel())
                .ipWhitelist(source.getIpWhitelist())
                .orgDirectoryBaseUrl(source.getOrgDirectoryBaseUrl())
                .orgDirectoryAuthClientId(source.getOrgDirectoryAuthClientId())
                .orgDirectoryAuthClientSecret(source.getOrgDirectoryAuthClientSecret())
                .orgDirectoryUserDetailPath(source.getOrgDirectoryUserDetailPath())
                .orgDirectoryDeptDetailPath(source.getOrgDirectoryDeptDetailPath())
                .orgDirectoryUserWindowPath(source.getOrgDirectoryUserWindowPath())
                .orgDirectoryDeptWindowPath(source.getOrgDirectoryDeptWindowPath())
                .orgEventSdkEnabled(source.getOrgEventSdkEnabled())
                .orgEventConsumerGroup(source.getOrgEventConsumerGroup())
                .orgEventServerRegisterUrl(source.getOrgEventServerRegisterUrl())
                .build();
    }

    List<SettingsResponse.RoleSetting> copyRoles(List<SettingsResponse.RoleSetting> source) {
        List<SettingsResponse.RoleSetting> target = new ArrayList<>();
        for (SettingsResponse.RoleSetting item : nullToList(source)) {
            target.add(SettingsResponse.RoleSetting.builder()
                    .code(item.getCode())
                    .name(item.getName())
                    .description(item.getDescription())
                    .userCount(item.getUserCount())
                    .dataScope(item.getDataScope())
                    .menuPermissions(copyStringList(item.getMenuPermissions()))
                    .allowedProjects(copyStringList(item.getAllowedProjects()))
                    .allowedDepts(copyStringList(item.getAllowedDepts()))
                    .build());
        }
        return target;
    }

    List<SettingsResponse.RoleSetting> copyRolesFromUpdate(List<SettingsUpdateRequest.RoleSettingUpdate> source) {
        List<SettingsResponse.RoleSetting> target = new ArrayList<>();
        for (SettingsUpdateRequest.RoleSettingUpdate item : nullToList(source)) {
            target.add(SettingsResponse.RoleSetting.builder()
                    .code(item.getCode())
                    .name(item.getName())
                    .description(item.getDescription())
                    .userCount(item.getUserCount())
                    .dataScope(item.getDataScope())
                    .menuPermissions(copyStringList(item.getMenuPermissions()))
                    .allowedProjects(copyStringList(item.getAllowedProjects()))
                    .allowedDepts(copyStringList(item.getAllowedDepts()))
                    .build());
        }
        return target;
    }

    List<SettingsResponse.DeptDataScopeSetting> copyDeptScopes(List<SettingsResponse.DeptDataScopeSetting> source) {
        List<SettingsResponse.DeptDataScopeSetting> target = new ArrayList<>();
        for (SettingsResponse.DeptDataScopeSetting item : nullToList(source)) {
            target.add(SettingsResponse.DeptDataScopeSetting.builder()
                    .deptName(item.getDeptName())
                    .dataScope(item.getDataScope())
                    .canViewOtherDepts(item.getCanViewOtherDepts())
                    .allowedDepts(copyStringList(item.getAllowedDepts()))
                    .build());
        }
        return target;
    }

    List<SettingsResponse.DeptDataScopeSetting> copyDeptScopesFromUpdate(List<SettingsUpdateRequest.DeptDataScopeUpdate> source) {
        List<SettingsResponse.DeptDataScopeSetting> target = new ArrayList<>();
        for (SettingsUpdateRequest.DeptDataScopeUpdate item : nullToList(source)) {
            target.add(SettingsResponse.DeptDataScopeSetting.builder()
                    .deptName(item.getDeptName())
                    .dataScope(item.getDataScope())
                    .canViewOtherDepts(item.getCanViewOtherDepts())
                    .allowedDepts(copyStringList(item.getAllowedDepts()))
                    .build());
        }
        return target;
    }

    List<SettingsResponse.ProjectGroupScopeSetting> copyProjectScopes(List<SettingsResponse.ProjectGroupScopeSetting> source) {
        List<SettingsResponse.ProjectGroupScopeSetting> target = new ArrayList<>();
        for (SettingsResponse.ProjectGroupScopeSetting item : nullToList(source)) {
            target.add(SettingsResponse.ProjectGroupScopeSetting.builder()
                    .groupName(item.getGroupName())
                    .manager(item.getManager())
                    .memberCount(item.getMemberCount())
                    .visibility(item.getVisibility())
                    .allowedRoles(copyStringList(item.getAllowedRoles()))
                    .build());
        }
        return target;
    }

    List<SettingsResponse.ProjectGroupScopeSetting> copyProjectScopesFromUpdate(List<SettingsUpdateRequest.ProjectGroupScopeUpdate> source) {
        List<SettingsResponse.ProjectGroupScopeSetting> target = new ArrayList<>();
        for (SettingsUpdateRequest.ProjectGroupScopeUpdate item : nullToList(source)) {
            target.add(SettingsResponse.ProjectGroupScopeSetting.builder()
                    .groupName(item.getGroupName())
                    .manager(item.getManager())
                    .memberCount(item.getMemberCount())
                    .visibility(item.getVisibility())
                    .allowedRoles(copyStringList(item.getAllowedRoles()))
                    .build());
        }
        return target;
    }

    List<SettingsResponse.FlowMappingSetting> copyFlowMappings(List<SettingsResponse.FlowMappingSetting> source) {
        List<SettingsResponse.FlowMappingSetting> target = new ArrayList<>();
        for (SettingsResponse.FlowMappingSetting item : nullToList(source)) {
            target.add(SettingsResponse.FlowMappingSetting.builder()
                    .systemFlow(item.getSystemFlow())
                    .oaFlow(item.getOaFlow())
                    .oaFlowCode(item.getOaFlowCode())
                    .oaFlowName(item.getOaFlowName())
                    .description(item.getDescription())
                    .build());
        }
        return target;
    }

    List<SettingsResponse.FlowMappingSetting> copyFlowMappingsFromUpdate(List<SettingsUpdateRequest.FlowMappingUpdate> source) {
        List<SettingsResponse.FlowMappingSetting> target = new ArrayList<>();
        for (SettingsUpdateRequest.FlowMappingUpdate item : nullToList(source)) {
            target.add(SettingsResponse.FlowMappingSetting.builder()
                    .systemFlow(item.getSystemFlow())
                    .oaFlow(item.getOaFlow())
                    .oaFlowCode(item.getOaFlowCode())
                    .oaFlowName(item.getOaFlowName())
                    .description(item.getDescription())
                    .build());
        }
        return target;
    }

    List<SettingsResponse.ApiSetting> copyApiSettings(List<SettingsResponse.ApiSetting> source) {
        List<SettingsResponse.ApiSetting> target = new ArrayList<>();
        for (SettingsResponse.ApiSetting item : nullToList(source)) {
            target.add(SettingsResponse.ApiSetting.builder()
                    .name(item.getName())
                    .path(item.getPath())
                    .method(item.getMethod())
                    .description(item.getDescription())
                    .status(item.getStatus())
                    .enabled(item.getEnabled())
                    .build());
        }
        return target;
    }

    List<SettingsResponse.ApiSetting> copyApiSettingsFromUpdate(List<SettingsUpdateRequest.ApiSettingUpdate> source) {
        List<SettingsResponse.ApiSetting> target = new ArrayList<>();
        for (SettingsUpdateRequest.ApiSettingUpdate item : nullToList(source)) {
            target.add(SettingsResponse.ApiSetting.builder()
                    .name(item.getName())
                    .path(item.getPath())
                    .method(item.getMethod())
                    .description(item.getDescription())
                    .status(item.getStatus())
                    .enabled(item.getEnabled())
                    .build());
        }
        return target;
    }

    List<String> copyStringList(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    private <T> List<T> nullToList(List<T> source) {
        return source == null ? List.of() : source;
    }
}
