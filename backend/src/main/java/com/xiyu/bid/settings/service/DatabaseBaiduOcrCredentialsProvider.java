// Input: SettingsService persistent settings
// Output: Effective OCR gateway credentials from encrypted system settings
// Pos: settings/service - Baidu OCR credentials provider adapter
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.settings.service;

import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduOcrCredentialsProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 基于系统配置（SystemSetting JSON payload）持久化记录的百度表格识别凭证提供者。
 */
@Component
public class DatabaseBaiduOcrCredentialsProvider implements BaiduOcrCredentialsProvider {

    private final SettingsService settingsService;

    public DatabaseBaiduOcrCredentialsProvider(@Lazy SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public boolean isEnabled() {
        return settingsService.getEffectiveBaiduCredentials().enabled();
    }

    @Override
    public String getApiKey() {
        return settingsService.getEffectiveBaiduCredentials().apiKey();
    }

    @Override
    public String getSecretKey() {
        return settingsService.getEffectiveBaiduCredentials().secretKey();
    }
    @Override
    public String getUsername() {
        return settingsService.getEffectiveBaiduCredentials().username();
    }

    @Override
    public String getPassword() {
        return settingsService.getEffectiveBaiduCredentials().password();
    }
}
