package com.xiyu.bid.bootstrap;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class StartupConfigurationSummaryLogger implements ApplicationRunner {

    private final Environment environment;
    private final CrmProperties crmProperties;
    private final OrganizationIntegrationProperties organizationProperties;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Startup configuration summary: profiles={}, serverPort={}", activeProfiles(), serverPort());
        log.info("CRM configuration summary: authBaseUrlConfigured={}, chanceBaseUrlConfigured={}, "
                        + "contactPersonBaseUrlConfigured={}, oauthUsernameConfigured={}, oauthPasswordConfigured={}, "
                        + "clientIdConfigured={}, clientSecretConfigured={}, generateTokenNickNameConfigured={}, "
                        + "generateTokenSalesNoConfigured={}, matchingStrategy={}",
                configured(crmProperties.getEffectiveAuthBaseUrl()),
                configured(crmProperties.getEffectiveChanceBaseUrl()),
                configured(crmProperties.getEffectiveContactPersonBaseUrl()),
                configured(crmProperties.getOauthUsername()),
                configured(crmProperties.getOauthPassword()),
                configured(crmProperties.getClientId()),
                configured(crmProperties.getClientSecret()),
                configured(crmProperties.getGenerateTokenNickName()),
                configured(crmProperties.getGenerateTokenSalesNo()),
                crmProperties.getMatchingStrategy());
        log.info("Organization integration summary: enabled={}, eventSdkEnabled={}, directoryBaseUrlConfigured={}, "
                        + "sourceAppConfigured={}, autoSyncMenuPermissions={}, retryEnabled={}, reconciliationEnabled={}",
                organizationProperties.isEnabled(),
                organizationProperties.getEventSdk().isEnabled(),
                configured(organizationProperties.getDirectory().getBaseUrl()),
                configured(organizationProperties.getDirectory().getSourceApp()),
                organizationProperties.getDirectory().isAutoSyncMenuPermissions(),
                organizationProperties.getRetry().isEnabled(),
                organizationProperties.getReconciliation().isEnabled());
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "[]";
        }
        return Arrays.toString(profiles);
    }

    private String serverPort() {
        return environment.getProperty("server.port", "8080");
    }

    private boolean configured(String value) {
        return StringUtils.hasText(value);
    }
}
