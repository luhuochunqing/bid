package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OrganizationIntegrationSettingsResolver")
class OrganizationIntegrationSettingsResolverTest {

    @Test
    @DisplayName("system settings override yml switch, secret and ip whitelist")
    void resolve_usesSystemSettingsFirst() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(SettingsResponse.builder()
                .integrationConfig(SettingsResponse.IntegrationConfig.builder()
                        .orgEnabled(false)
                        .orgSystem("xiyu-event-sdk")
                        .orgAppKey("customer-org")
                        .orgAppSecret("settings-secret")
                        .ipWhitelist("10.0.0.1,10.0.0.2")
                        .build())
                .build());
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(true);
        properties.setWebhookSecret("yml-secret");
        properties.setAllowedSourceApps(List.of("legacy-source"));

        OrganizationIntegrationSettings resolved =
                new OrganizationIntegrationSettingsResolver(settingsService, properties).resolve();

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.webhookSecret()).isEqualTo("settings-secret");
        assertThat(resolved.sourceAllowed("customer-org")).isTrue();
        assertThat(resolved.sourceAllowed("xiyu-event-sdk")).isTrue();
        assertThat(resolved.sourceAllowed("legacy-source")).isTrue();
        assertThat(resolved.ipAllowed("10.0.0.2")).isTrue();
        assertThat(resolved.ipAllowed("10.0.0.9")).isFalse();
    }

    @Test
    @DisplayName("blank system settings fall back to yml defaults")
    void resolve_fallsBackToProperties() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(SettingsResponse.builder()
                .integrationConfig(SettingsResponse.IntegrationConfig.builder()
                        .orgEnabled(null)
                        .orgAppSecret("")
                        .ipWhitelist("")
                        .build())
                .build());
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(false);
        properties.setWebhookSecret("yml-secret");
        properties.setIpWhitelist("127.0.0.1");
        properties.setAllowedSourceApps(List.of("customer-org"));

        OrganizationIntegrationSettings resolved =
                new OrganizationIntegrationSettingsResolver(settingsService, properties).resolve();

        assertThat(resolved.enabled()).isFalse();
        assertThat(resolved.webhookSecret()).isEqualTo("yml-secret");
        assertThat(resolved.sourceAllowed("customer-org")).isTrue();
        assertThat(resolved.ipAllowed("127.0.0.1")).isTrue();
        assertThat(resolved.ipAllowed("127.0.0.2")).isFalse();
    }
}
