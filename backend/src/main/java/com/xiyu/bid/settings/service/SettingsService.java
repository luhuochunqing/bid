package com.xiyu.bid.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsService {

    private static final String DEFAULT_CONFIG_KEY = "default";

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final SettingsPayloadMapper payloadMapper;
    private final ObjectReader settingsReader;
    private final ObjectWriter settingsWriter;
    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final AiProviderCatalog aiProviderCatalog;

    public SettingsService(
            SystemSettingRepository pSystemSettingRepository,
            UserRepository pUserRepository,
            ObjectMapper objectMapper,
            SettingsPayloadMapper pPayloadMapper,
            PasswordEncryptionUtil pPasswordEncryptionUtil,
            AiProviderCatalog pAiProviderCatalog
    ) {
        this.systemSettingRepository = pSystemSettingRepository;
        this.userRepository = pUserRepository;
        this.payloadMapper = pPayloadMapper;
        this.settingsReader = objectMapper.readerFor(SettingsResponse.class);
        this.settingsWriter = objectMapper.writerFor(SettingsResponse.class);
        this.passwordEncryptionUtil = pPasswordEncryptionUtil;
        this.aiProviderCatalog = pAiProviderCatalog;
    }

    @Transactional
    public SettingsResponse getSettings() {
        return copyForResponse(readSettings());
    }

    @Transactional
    public SettingsResponse updateSettings(SettingsUpdateRequest request) {
        SettingsResponse current = readSettings();

        if (request.getSystemConfig() != null) {
            current.setSystemConfig(payloadMapper.copySystemConfigFromUpdate(request.getSystemConfig()));
        }
        if (request.getRoles() != null) {
            current.setRoles(payloadMapper.copyRolesFromUpdate(request.getRoles()));
        }
        if (request.getDeptDataScope() != null) {
            current.setDeptDataScope(payloadMapper.copyDeptScopesFromUpdate(request.getDeptDataScope()));
        }
        if (request.getProjectGroupScope() != null) {
            current.setProjectGroupScope(payloadMapper.copyProjectScopesFromUpdate(request.getProjectGroupScope()));
        }
        if (request.getIntegrationConfig() != null) {
            current.setIntegrationConfig(payloadMapper.copyIntegrationConfigFromUpdate(request.getIntegrationConfig()));
        }
        if (request.getAiModelConfig() != null) {
            current.setAiModelConfig(mergeAiModelConfig(current.getAiModelConfig(), request.getAiModelConfig()));
        }
        if (request.getFlowMappings() != null) {
            current.setFlowMappings(payloadMapper.copyFlowMappingsFromUpdate(request.getFlowMappings()));
        }
        if (request.getApiList() != null) {
            current.setApiList(payloadMapper.copyApiSettingsFromUpdate(request.getApiList()));
        }

        saveSettings(current);
        return copyForResponse(current);
    }

    @Transactional(readOnly = true)
    public SettingsResponse.RuntimePermissionProfile getRuntimePermissionProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String roleCode = user.getRoleCode();
        SettingsResponse.RoleSetting roleSetting = readSettings().getRoles().stream()
                .filter(role -> roleCode.equalsIgnoreCase(role.getCode()))
                .findFirst()
                .orElse(SettingsResponse.RoleSetting.builder()
                        .code(roleCode)
                        .menuPermissions(List.of())
                        .dataScope("self")
                        .allowedProjects(List.of())
                        .allowedDepts(List.of())
                        .build());

        return SettingsResponse.RuntimePermissionProfile.builder()
                .code(roleSetting.getCode())
                .menuPermissions(payloadMapper.copyStringList(roleSetting.getMenuPermissions()))
                .dataScope(roleSetting.getDataScope())
                .allowedProjects(payloadMapper.copyStringList(roleSetting.getAllowedProjects()))
                .allowedDepts(payloadMapper.copyStringList(roleSetting.getAllowedDepts()))
                .build();
    }

    public SettingsResponse.AiModelConfig getInternalAiModelConfig() {
        return normalizeAiModelConfig(readSettings().getAiModelConfig());
    }

    public boolean isAiEnabled() {
        SettingsResponse.SystemConfig systemConfig = readSettings().getSystemConfig();
        return systemConfig == null || systemConfig.getEnableAI() == null || systemConfig.getEnableAI();
    }

    public String resolveAiApiKey(String providerCode) {
        SettingsResponse.AiProviderSetting provider = findAiProvider(getInternalAiModelConfig(), providerCode);
        if (provider == null || provider.getEncryptedApiKey() == null || provider.getEncryptedApiKey().isBlank()) {
            return null;
        }
        return passwordEncryptionUtil.decrypt(provider.getEncryptedApiKey());
    }

    @Transactional
    public SettingsResponse.AiModelConfig updateAiProviderTestResult(
            String providerCode,
            String status,
            String message
    ) {
        SettingsResponse settings = readSettings();
        SettingsResponse.AiModelConfig config = normalizeAiModelConfig(settings.getAiModelConfig());
        SettingsResponse.AiProviderSetting provider = findAiProvider(config, providerCode);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported AI provider: " + providerCode);
        }
        provider.setLastTestStatus(status);
        provider.setLastTestMessage(message);
        provider.setLastTestAt(Instant.now());
        settings.setAiModelConfig(config);
        saveSettings(settings);
        return copyAiModelConfigForResponse(config);
    }

    @Transactional
    public SettingsResponse.AiModelConfig saveSuccessfulAiProviderTestConfig(
            String providerCode,
            String baseUrl,
            String model,
            String apiKeyPlaintext,
            String message
    ) {
        SettingsResponse settings = readSettings();
        SettingsResponse.AiModelConfig config = normalizeAiModelConfig(settings.getAiModelConfig());
        String normalizedProviderCode = normalizeProviderCode(providerCode);
        SettingsResponse.AiProviderSetting provider = findAiProvider(config, normalizedProviderCode);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported AI provider: " + providerCode);
        }

        if (baseUrl != null && !baseUrl.isBlank()) {
            aiProviderCatalog.validateBaseUrl(normalizedProviderCode, baseUrl);
            provider.setBaseUrl(baseUrl.trim());
        }
        if (model != null && !model.isBlank()) {
            provider.setModel(model.trim());
        }
        if (apiKeyPlaintext != null && !apiKeyPlaintext.isBlank()) {
            provider.setEncryptedApiKey(passwordEncryptionUtil.encrypt(apiKeyPlaintext.trim()));
        }
        provider.setLastTestStatus("success");
        provider.setLastTestMessage(message);
        provider.setLastTestAt(Instant.now());
        settings.setAiModelConfig(config);
        saveSettings(settings);
        return copyAiModelConfigForResponse(config);
    }

    private SettingsResponse readSettings() {
        return systemSettingRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                .map(SystemSetting::getPayloadJson)
                .map(this::deserialize)
                .orElseGet(this::createAndPersistDefaultSettings);
    }

    private SettingsResponse createAndPersistDefaultSettings() {
        SettingsResponse defaults = payloadMapper.createDefaultSettings();
        defaults.setAiModelConfig(defaultAiModelConfig());
        saveSettings(defaults);
        return defaults;
    }

    private void saveSettings(SettingsResponse settings) {
        SystemSetting record = systemSettingRepository.findByConfigKey(DEFAULT_CONFIG_KEY)
                .orElseGet(() -> SystemSetting.builder().configKey(DEFAULT_CONFIG_KEY).build());
        record.setPayloadJson(serialize(settings));
        systemSettingRepository.save(record);
    }

    private SettingsResponse deserialize(String payloadJson) {
        try {
            return settingsReader.readValue(payloadJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize persisted settings payload", exception);
        }
    }

    private String serialize(SettingsResponse settings) {
        try {
            return settingsWriter.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize settings payload", exception);
        }
    }

    private SettingsResponse copyForResponse(SettingsResponse source) {
        SettingsResponse copied = payloadMapper.copy(source);
        copied.setAiModelConfig(copyAiModelConfigForResponse(source.getAiModelConfig()));
        return copied;
    }

    private SettingsResponse.AiModelConfig mergeAiModelConfig(
            SettingsResponse.AiModelConfig current,
            SettingsUpdateRequest.AiModelConfigUpdate update
    ) {
        SettingsResponse.AiModelConfig normalizedCurrent = normalizeAiModelConfig(current);
        if (update.getActiveProvider() != null && !update.getActiveProvider().isBlank()) {
            normalizedCurrent.setActiveProvider(normalizeProviderCode(update.getActiveProvider()));
        }

        Map<String, SettingsResponse.AiProviderSetting> providerMap = new HashMap<>();
        for (SettingsResponse.AiProviderSetting provider : normalizedCurrent.getProviders()) {
            providerMap.put(provider.getProviderCode(), provider);
        }

        if (update.getProviders() != null) {
            for (SettingsUpdateRequest.AiProviderSettingUpdate providerUpdate : update.getProviders()) {
                String providerCode = normalizeProviderCode(providerUpdate.getProviderCode());
                if (!aiProviderCatalog.isSupported(providerCode)) {
                    continue;
                }
                SettingsResponse.AiProviderSetting target = providerMap.get(providerCode);
                if (target == null) {
                    target = defaultAiProviderSetting(providerCode);
                    providerMap.put(providerCode, target);
                }
                if (providerUpdate.getEnabled() != null) {
                    target.setEnabled(providerUpdate.getEnabled());
                }
                if (providerUpdate.getBaseUrl() != null) {
                    aiProviderCatalog.validateBaseUrl(providerCode, providerUpdate.getBaseUrl());
                    target.setBaseUrl(providerUpdate.getBaseUrl().trim());
                }
                if (providerUpdate.getModel() != null) {
                    target.setModel(providerUpdate.getModel().trim());
                }
                if (providerUpdate.getApiKeyPlaintext() != null && !providerUpdate.getApiKeyPlaintext().isBlank()) {
                    target.setEncryptedApiKey(passwordEncryptionUtil.encrypt(providerUpdate.getApiKeyPlaintext().trim()));
                }
                if (providerUpdate.getLastTestStatus() != null) {
                    target.setLastTestStatus(providerUpdate.getLastTestStatus());
                }
                if (providerUpdate.getLastTestMessage() != null) {
                    target.setLastTestMessage(providerUpdate.getLastTestMessage());
                }
                if (providerUpdate.getLastTestAt() != null) {
                    target.setLastTestAt(providerUpdate.getLastTestAt());
                }
            }
        }

        normalizedCurrent.setProviders(aiProviderCatalog.supportedProviderCodes().stream()
                .map(code -> providerMap.getOrDefault(code, defaultAiProviderSetting(code)))
                .toList());
        if (!aiProviderCatalog.isSupported(normalizedCurrent.getActiveProvider())) {
            normalizedCurrent.setActiveProvider(aiProviderCatalog.defaultActiveProvider());
        }
        return normalizedCurrent;
    }

    private SettingsResponse.AiModelConfig normalizeAiModelConfig(SettingsResponse.AiModelConfig source) {
        SettingsResponse.AiModelConfig defaults = defaultAiModelConfig();
        if (source == null) {
            return defaults;
        }

        Map<String, SettingsResponse.AiProviderSetting> sourceProviders = new HashMap<>();
        if (source.getProviders() != null) {
            for (SettingsResponse.AiProviderSetting provider : source.getProviders()) {
                String providerCode = normalizeProviderCode(provider.getProviderCode());
                if (!aiProviderCatalog.isSupported(providerCode)) {
                    continue;
                }
                SettingsResponse.AiProviderSetting merged = defaultAiProviderSetting(providerCode);
                merged.setEnabled(provider.getEnabled() != null ? provider.getEnabled() : merged.getEnabled());
                merged.setBaseUrl(nonBlankOrDefault(provider.getBaseUrl(), merged.getBaseUrl()));
                merged.setModel(nonBlankOrDefault(provider.getModel(), merged.getModel()));
                merged.setEncryptedApiKey(provider.getEncryptedApiKey());
                merged.setLastTestStatus(provider.getLastTestStatus());
                merged.setLastTestMessage(provider.getLastTestMessage());
                merged.setLastTestAt(provider.getLastTestAt());
                sourceProviders.put(providerCode, merged);
            }
        }

        defaults.setActiveProvider(aiProviderCatalog.isSupported(normalizeProviderCode(source.getActiveProvider()))
                ? normalizeProviderCode(source.getActiveProvider())
                : aiProviderCatalog.defaultActiveProvider());
        defaults.setProviders(aiProviderCatalog.supportedProviderCodes().stream()
                .map(code -> sourceProviders.getOrDefault(code, defaultAiProviderSetting(code)))
                .toList());
        return defaults;
    }

    private SettingsResponse.AiModelConfig defaultAiModelConfig() {
        return SettingsResponse.AiModelConfig.builder()
                .activeProvider(aiProviderCatalog.defaultActiveProvider())
                .providers(aiProviderCatalog.supportedProviderCodes().stream().map(this::defaultAiProviderSetting).toList())
                .build();
    }

    private SettingsResponse.AiProviderSetting defaultAiProviderSetting(String providerCode) {
        return aiProviderCatalog.defaultProviderSetting(providerCode);
    }

    private SettingsResponse.AiModelConfig copyAiModelConfigForResponse(SettingsResponse.AiModelConfig source) {
        SettingsResponse.AiModelConfig normalized = normalizeAiModelConfig(source);
        return SettingsResponse.AiModelConfig.builder()
                .activeProvider(normalized.getActiveProvider())
                .providers(normalized.getProviders().stream()
                        .map(this::copyAiProviderForResponse)
                        .toList())
                .build();
    }

    private SettingsResponse.AiProviderSetting copyAiProviderForResponse(SettingsResponse.AiProviderSetting source) {
        String encryptedApiKey = source.getEncryptedApiKey();
        String plaintext = null;
        if (encryptedApiKey != null && !encryptedApiKey.isBlank()) {
            try {
                plaintext = passwordEncryptionUtil.decrypt(encryptedApiKey);
            } catch (RuntimeException ignored) {
                plaintext = null;
            }
        }
        return SettingsResponse.AiProviderSetting.builder()
                .providerCode(source.getProviderCode())
                .providerName(source.getProviderName())
                .enabled(source.getEnabled())
                .baseUrl(source.getBaseUrl())
                .model(source.getModel())
                .apiKeyMasked(maskApiKey(plaintext))
                .apiKeyConfigured(plaintext != null && !plaintext.isBlank())
                .lastTestStatus(source.getLastTestStatus())
                .lastTestMessage(source.getLastTestMessage())
                .lastTestAt(source.getLastTestAt())
                .build();
    }

    private SettingsResponse.AiProviderSetting findAiProvider(SettingsResponse.AiModelConfig config, String providerCode) {
        String normalizedCode = normalizeProviderCode(providerCode);
        if (config == null || config.getProviders() == null) {
            return null;
        }
        return config.getProviders().stream()
                .filter(provider -> normalizedCode.equals(provider.getProviderCode()))
                .findFirst()
                .orElse(null);
    }

    private String normalizeProviderCode(String providerCode) {
        return aiProviderCatalog.normalize(providerCode);
    }

    private String nonBlankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****" + trimmed.substring(Math.max(0, trimmed.length() - 2));
        }
        return trimmed.substring(0, Math.min(4, trimmed.length())) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
