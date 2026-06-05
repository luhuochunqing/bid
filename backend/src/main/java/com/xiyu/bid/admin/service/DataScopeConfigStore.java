package com.xiyu.bid.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.dto.DataScopeConfigPayload;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataScopeConfigStore {
    public static final String DATA_SCOPE_CONFIG_KEY = "data_scope_config";

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    public DataScopeConfigPayload loadPayload() {
        return systemSettingRepository.findByConfigKey(DATA_SCOPE_CONFIG_KEY)
                .map(SystemSetting::getPayloadJson)
                .filter(value -> value != null && !value.isBlank())
                .map(this::deserialize)
                .orElseGet(() -> DataScopeConfigPayload.builder().build());
    }

    public void savePayload(DataScopeConfigPayload payload) {
        SystemSetting setting = systemSettingRepository.findByConfigKey(DATA_SCOPE_CONFIG_KEY)
                .orElseGet(() -> SystemSetting.builder().configKey(DATA_SCOPE_CONFIG_KEY).build());
        setting.setPayloadJson(serialize(payload));
        systemSettingRepository.save(setting);
    }

    private DataScopeConfigPayload deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, DataScopeConfigPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("数据权限配置读取失败", ex);
        }
    }

    private String serialize(DataScopeConfigPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize data scope payload", ex);
            throw new IllegalStateException("数据权限配置保存失败", ex);
        }
    }
}
