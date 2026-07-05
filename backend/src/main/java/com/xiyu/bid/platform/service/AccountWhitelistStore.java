package com.xiyu.bid.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台账户白名单 Store 基类。
 *
 * <p>从 system_settings 表读取用户名白名单，payload 为 JSON 数组（如 {@code ["00444"]}）。
 * 子类只需指定 {@link #configKey()}，无需重复实现加载/反序列化逻辑。</p>
 *
 * <p>对称于 {@code DataScopeConfigStore} 的设计范式。</p>
 */
@Slf4j
public abstract class AccountWhitelistStore {

    private static final List<String> EMPTY = List.of();

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    protected AccountWhitelistStore(SystemSettingRepository systemSettingRepository, ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.objectMapper = objectMapper;
    }

    /** 子类指定 system_settings 中的 config_key。 */
    protected abstract String configKey();

    /** 加载白名单用户名列表，配置不存在时返回空列表。 */
    public List<String> loadWhitelist() {
        return systemSettingRepository.findByConfigKey(configKey())
                .map(SystemSetting::getPayloadJson)
                .filter(json -> json != null && !json.isBlank())
                .map(this::deserialize)
                .orElse(EMPTY);
    }

    private List<String> deserialize(String json) {
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list != null ? list : EMPTY;
        } catch (JsonProcessingException ex) {
            log.warn("平台账户白名单配置读取失败（config_key={}），返回空列表", configKey(), ex);
            return EMPTY;
        }
    }
}
