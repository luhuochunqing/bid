package com.xiyu.bid.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.springframework.stereotype.Component;

/**
 * 平台账户导出白名单 Store。
 *
 * <p>config_key = {@value #CONFIG_KEY}，payload 为 JSON 数组（如 {@code ["00444"]}）。</p>
 */
@Component
public class AccountExportWhitelistStore extends AccountWhitelistStore {

    public static final String CONFIG_KEY = "platform_account_export_whitelist";

    public AccountExportWhitelistStore(SystemSettingRepository systemSettingRepository, ObjectMapper objectMapper) {
        super(systemSettingRepository, objectMapper);
    }

    @Override
    protected String configKey() {
        return CONFIG_KEY;
    }

    /** 便捷方法：校验导出权限，不放行则抛 AccessDeniedException。 */
    public void checkExportPermission(String roleCode, User currentUser) {
        PlatformAccountViewerPolicy.checkCanExportAccount(roleCode, currentUser, loadWhitelist());
    }
}
