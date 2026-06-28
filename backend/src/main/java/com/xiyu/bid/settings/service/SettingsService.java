package com.xiyu.bid.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SettingsService {

    private static final String DEFAULT_CONFIG_KEY = "default";
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final SettingsPayloadMapper payloadMapper;
    private final ObjectReader settingsReader;
    private final ObjectWriter settingsWriter;
    private final AiConfigService aiConfigService;
    private final EffectiveRoleResolver effectiveRoleResolver;

    public SettingsService(
            SystemSettingRepository pSystemSettingRepository,
            UserRepository pUserRepository,
            ObjectMapper objectMapper,
            SettingsPayloadMapper pPayloadMapper,
            AiConfigService aiConfigService,
            EffectiveRoleResolver pEffectiveRoleResolver
    ) {
        this.systemSettingRepository = pSystemSettingRepository;
        this.userRepository = pUserRepository;
        this.payloadMapper = pPayloadMapper;
        this.settingsReader = objectMapper.readerFor(SettingsResponse.class);
        this.settingsWriter = objectMapper.writerFor(SettingsResponse.class);
        this.aiConfigService = aiConfigService;
        this.effectiveRoleResolver = pEffectiveRoleResolver;
    }

    @Transactional
    public SettingsResponse getSettings() { return copyForResponse(readSettings()); }

    @Transactional
    public SettingsResponse updateSettings(SettingsUpdateRequest request) {
        SettingsResponse current = readSettings();
        if (request.getSystemConfig() != null) current.setSystemConfig(payloadMapper.copySystemConfigFromUpdate(request.getSystemConfig()));
        if (request.getRoles() != null) current.setRoles(payloadMapper.copyRolesFromUpdate(request.getRoles()));
        if (request.getDeptDataScope() != null) current.setDeptDataScope(payloadMapper.copyDeptScopesFromUpdate(request.getDeptDataScope()));
        if (request.getProjectGroupScope() != null) current.setProjectGroupScope(payloadMapper.copyProjectScopesFromUpdate(request.getProjectGroupScope()));
        if (request.getIntegrationConfig() != null) current.setIntegrationConfig(payloadMapper.copyIntegrationConfigFromUpdate(request.getIntegrationConfig()));
        if (request.getAiModelConfig() != null) current.setAiModelConfig(aiConfigService.mergeAiModelConfig(current.getAiModelConfig(), request.getAiModelConfig()));
        if (request.getFlowMappings() != null) current.setFlowMappings(payloadMapper.copyFlowMappingsFromUpdate(request.getFlowMappings()));
        if (request.getApiList() != null) current.setApiList(payloadMapper.copyApiSettingsFromUpdate(request.getApiList()));
        saveSettings(current);
        return copyForResponse(current);
    }

    @Transactional(readOnly = true)
    public SettingsResponse.RuntimePermissionProfile getRuntimePermissionProfile(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        // CO-373：统一走 EffectiveRoleResolver，OSS 用户以缓存角色码为准
        String roleCode = effectiveRoleResolver.resolveRoleCode(user);
        SettingsResponse.RoleSetting roleSetting = readSettings().getRoles().stream()
                .filter(role -> roleCode != null && roleCode.equalsIgnoreCase(role.getCode())).findFirst()
                .orElse(SettingsResponse.RoleSetting.builder().code(roleCode).menuPermissions(List.of()).dataScope("self").allowedProjects(List.of()).allowedDepts(List.of()).build());
        return SettingsResponse.RuntimePermissionProfile.builder()
                .code(roleSetting.getCode()).menuPermissions(payloadMapper.copyStringList(roleSetting.getMenuPermissions()))
                .dataScope(roleSetting.getDataScope()).allowedProjects(payloadMapper.copyStringList(roleSetting.getAllowedProjects()))
                .allowedDepts(payloadMapper.copyStringList(roleSetting.getAllowedDepts())).build();
    }

    SettingsResponse getSettingsInternal() { return readSettings(); }
    void saveSettingsInternal(SettingsResponse settings) { saveSettings(settings); }

    private SettingsResponse readSettings() {
        return systemSettingRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                .map(SystemSetting::getPayloadJson).map(this::deserialize)
                .orElseGet(this::createAndPersistDefaultSettings);
    }
    private SettingsResponse createAndPersistDefaultSettings() {
        SettingsResponse defaults = payloadMapper.createDefaultSettings();
        defaults.setAiModelConfig(aiConfigService.normalizeAiModelConfig(null));
        saveSettings(defaults);
        return defaults;
    }
    private void saveSettings(SettingsResponse settings) {
        SystemSetting record = systemSettingRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                .orElseGet(() -> SystemSetting.builder().configKey(DEFAULT_CONFIG_KEY).build());
        record.setPayloadJson(serialize(settings));
        systemSettingRepository.save(record);
    }
    private SettingsResponse deserialize(String json) { try { return settingsReader.readValue(json); } catch (JsonProcessingException e) { throw new IllegalStateException("Failed to deserialize settings", e); } }
    private String serialize(SettingsResponse s) { try { return settingsWriter.writeValueAsString(s); } catch (JsonProcessingException e) { throw new IllegalStateException("Failed to serialize settings", e); } }
    private SettingsResponse copyForResponse(SettingsResponse source) {
        SettingsResponse copied = payloadMapper.copy(source);
        copied.setAiModelConfig(aiConfigService.copyAiModelConfigForResponse(source.getAiModelConfig()));
        return copied;
    }
}
