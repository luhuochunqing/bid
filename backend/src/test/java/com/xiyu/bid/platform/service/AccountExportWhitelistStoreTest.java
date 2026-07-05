package com.xiyu.bid.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.settings.entity.SystemSetting;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AccountExportWhitelistStore} 单元测试。
 */
@DisplayName("AccountExportWhitelistStore 导出白名单 Store")
@ExtendWith(MockitoExtension.class)
class AccountExportWhitelistStoreTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AccountExportWhitelistStore store;

    @Test
    @DisplayName("配置存在时返回白名单列表")
    void loadWhitelist_configExists_returnsList() {
        store = new AccountExportWhitelistStore(systemSettingRepository, objectMapper);
        SystemSetting setting = new SystemSetting();
        setting.setConfigKey(AccountExportWhitelistStore.CONFIG_KEY);
        setting.setPayloadJson("[\"00444\",\"00555\"]");
        when(systemSettingRepository.findByConfigKey(eq(AccountExportWhitelistStore.CONFIG_KEY)))
                .thenReturn(Optional.of(setting));

        assertThat(store.loadWhitelist()).containsExactly("00444", "00555");
    }

    @Test
    @DisplayName("配置不存在时返回空列表")
    void loadWhitelist_configMissing_returnsEmpty() {
        store = new AccountExportWhitelistStore(systemSettingRepository, objectMapper);
        when(systemSettingRepository.findByConfigKey(eq(AccountExportWhitelistStore.CONFIG_KEY)))
                .thenReturn(Optional.empty());

        assertThat(store.loadWhitelist()).isEmpty();
    }

    @Test
    @DisplayName("payload 为空白时返回空列表")
    void loadWhitelist_blankPayload_returnsEmpty() {
        store = new AccountExportWhitelistStore(systemSettingRepository, objectMapper);
        SystemSetting setting = new SystemSetting();
        setting.setPayloadJson("  ");
        when(systemSettingRepository.findByConfigKey(eq(AccountExportWhitelistStore.CONFIG_KEY)))
                .thenReturn(Optional.of(setting));

        assertThat(store.loadWhitelist()).isEmpty();
    }

    @Test
    @DisplayName("JSON 解析失败时返回空列表（优雅降级）")
    void loadWhitelist_invalidJson_returnsEmpty() {
        store = new AccountExportWhitelistStore(systemSettingRepository, objectMapper);
        SystemSetting setting = new SystemSetting();
        setting.setPayloadJson("not-a-json-array");
        when(systemSettingRepository.findByConfigKey(eq(AccountExportWhitelistStore.CONFIG_KEY)))
                .thenReturn(Optional.of(setting));

        assertThat(store.loadWhitelist()).isEmpty();
    }
}
