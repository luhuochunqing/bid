package com.xiyu.bid.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 平台账户导出白名单 Store。
 *
 * <p>从 system_settings 表读取可导出账户台账的用户名白名单，
 * config_key = {@value #CONFIG_KEY}，payload 为 JSON 数组（如 {@code ["00444"]}）。</p>
 *
 * <p>对称于 {@link AccountCreationWhitelistStore} 的设计范式。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountExportWhitelistStore {

    public static final String CONFIG_KEY = "platform_account_export_whitelist";

    private static final List<String> EMPTY = List.of();

    private final SystemSettingRepository systemSettingRepository;
    private final ObjectMapper objectMapper;

    /** 加载白名单用户名列表，配置不存在时返回空列表。 */
    public List<String> loadWhitelist() {
        return systemSettingRepository.findByConfigKey(CONFIG_KEY)
                .map(SystemSetting::getPayloadJson)
                .filter(json -> json != null && !json.isBlank())
                .map(this::deserialize)
                .orElse(EMPTY);
    }

    /** 便捷方法：校验导出权限，不放行则抛 AccessDeniedException。 */
    public void checkExportPermission(String roleCode, User currentUser) {
        PlatformAccountViewerPolicy.checkCanExportAccount(roleCode, currentUser, loadWhitelist());
    }

    private List<String> deserialize(String json) {
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list != null ? list : EMPTY;
        } catch (JsonProcessingException ex) {
            log.warn("平台账户导出白名单配置读取失败，返回空列表", ex);
            return EMPTY;
        }
    }
}
