package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityPropertiesTest {

    @Test
    void productionErrorExposureIsTightened() {
        Properties properties = productionProperties();

        assertThat(properties.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(properties.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        assertThat(properties.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }

    @Test
    void productionKeepsSensitiveSettingsExternalized() {
        Properties properties = productionProperties();

        assertPlaceholder(properties, "jwt.secret", "${JWT_SECRET}");
        assertPlaceholder(properties, "spring.datasource.password", "${DB_PASSWORD}");
        assertPlaceholder(properties, "app.bootstrap.admin.password", "${ADMIN_PASSWORD}");
        assertPlaceholder(properties, "cors.allowed-origins", "${CORS_ALLOWED_ORIGINS}");
    }

    @Test
    void productionDatasourceDefaultsToMysql8() {
        Properties properties = productionProperties();

        assertThat(properties.getProperty("spring.datasource.url")).contains("jdbc:mysql://");
        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.dialect"))
                .isEqualTo("org.hibernate.dialect.MySQLDialect");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration-mysql");
    }

    @Test
    void productionProfileKeepsCrmBindingsExternalized() {
        Properties properties = mergedProductionProperties();

        assertPlaceholder(properties, "app.crm.auth-base-url", "${XIYU_CRM_AUTH_BASE_URL:}");
        assertPlaceholder(properties, "app.crm.base-url", "${XIYU_CRM_BASE_URL:}");
        assertPlaceholder(properties, "app.crm.customer-base-url", "${XIYU_CRM_CUSTOMER_BASE_URL:}");
        assertPlaceholder(properties, "app.crm.message-base-url", "${XIYU_CRM_MESSAGE_BASE_URL:}");
        assertPlaceholder(properties, "app.crm.chance-base-url", "${XIYU_CRM_CHANCE_BASE_URL:}");
        assertPlaceholder(properties, "app.crm.contact-person-base-url", "${XIYU_CRM_CONTACT_PERSON_BASE_URL:}");
        assertPlaceholder(properties, "app.crm.client-id", "${XIYU_CRM_CLIENT_ID:}");
        assertPlaceholder(properties, "app.crm.client-secret", "${XIYU_CRM_CLIENT_SECRET:}");
        assertPlaceholder(properties, "app.crm.oauth-username", "${XIYU_CRM_OAUTH_USERNAME:}");
        assertPlaceholder(properties, "app.crm.oauth-password", "${XIYU_CRM_OAUTH_PASSWORD:}");
        assertPlaceholder(properties, "app.crm.oauth-system", "${XIYU_CRM_OAUTH_SYSTEM:HOME}");
        assertPlaceholder(properties, "app.crm.generate-token-path",
                "${XIYU_CRM_GENERATE_TOKEN_PATH:/common/inner/generateToken}");
        assertPlaceholder(properties, "app.crm.generate-token-nick-name",
                "${XIYU_CRM_GENERATE_TOKEN_NICK_NAME:}");
        assertPlaceholder(properties, "app.crm.generate-token-sales-no",
                "${XIYU_CRM_GENERATE_TOKEN_SALES_NO:}");
        assertPlaceholder(properties, "app.crm.auth.apply-token-path",
                "${XIYU_CRM_AUTH_APPLY_TOKEN_PATH:/auth/applyToken}");
        assertPlaceholder(properties, "app.crm.auth.oauth-login-path",
                "${XIYU_CRM_AUTH_OAUTH_LOGIN_PATH:/oauth/login}");
        assertPlaceholder(properties, "app.crm.auth.logout-path", "${XIYU_CRM_AUTH_LOGOUT_PATH:/auth/logout}");
        assertPlaceholder(properties, "app.crm.auth.menu-tree-path", "${XIYU_CRM_AUTH_MENU_TREE_PATH:/menu/tree}");
        assertPlaceholder(properties, "app.crm.auth.employee-path", "${XIYU_CRM_AUTH_EMPLOYEE_PATH:/employee/info}");
        assertPlaceholder(properties, "app.crm.customer.search-path",
                "${XIYU_CRM_CUSTOMER_SEARCH_PATH:/customer/search}");
        assertPlaceholder(properties, "app.crm.customer.contacts-path",
                "${XIYU_CRM_CUSTOMER_CONTACTS_PATH:/customer/contacts/batch}");
        assertPlaceholder(properties, "app.crm.message.send-path", "${XIYU_CRM_MESSAGE_SEND_PATH:/common/sendMessage}");
        assertPlaceholder(properties, "app.crm.matching-strategy", "${APP_CRM_MATCHING_STRATEGY:GROUP}");
    }

    @Test
    void productionProfileKeepsOrganizationIntegrationBindingsExternalized() {
        Properties properties = mergedProductionProperties();

        assertPlaceholder(properties, "xiyu.integrations.organization.enabled", "${XIYU_ORG_SYNC_ENABLED:false}");
        assertPlaceholder(properties, "xiyu.integrations.organization.ip-whitelist", "${XIYU_ORG_IP_WHITELIST:}");
        assertPlaceholder(properties, "xiyu.integrations.organization.event-log-retention-days",
                "${XIYU_ORG_EVENT_LOG_RETENTION_DAYS:90}");
        assertPlaceholder(properties, "xiyu.integrations.organization.allowed-source-apps",
                "${XIYU_ORG_ALLOWED_SOURCE_APPS:oss,customer-org}");
        assertPlaceholder(properties, "xiyu.integrations.organization.skip-unmapped-users",
                "${XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS:false}");
        assertPlaceholder(properties, "xiyu.integrations.organization.event-sdk.enabled",
                "${XIYU_ORG_EVENT_SDK_ENABLED:false}");
        assertPlaceholder(properties, "xiyu.integrations.organization.event-sdk.consumer-group",
                "${XIYU_ORG_EVENT_CONSUMER_GROUP:bms}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.base-url",
                "${XIYU_ORG_DIRECTORY_BASE_URL:}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.user-detail-path",
                "${XIYU_ORG_DIRECTORY_USER_DETAIL_PATH:/subscription/msg/user}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.department-detail-path",
                "${XIYU_ORG_DIRECTORY_DEPARTMENT_DETAIL_PATH:/subscription/msg/dept}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.user-window-path",
                "${XIYU_ORG_DIRECTORY_USER_WINDOW_PATH:/subscription/msg/getUserByTimeWindow}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.department-window-path",
                "${XIYU_ORG_DIRECTORY_DEPARTMENT_WINDOW_PATH:/subscription/msg/getDeptByTimeWindow}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.source-app",
                "${XIYU_ORG_DIRECTORY_SOURCE_APP:}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.trace-header-name",
                "${XIYU_ORG_DIRECTORY_TRACE_HEADER:EHSY-TraceID}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.source-header-name",
                "${XIYU_ORG_DIRECTORY_SOURCE_HEADER:EHSY-SRCAPP}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.connect-timeout-ms",
                "${XIYU_ORG_DIRECTORY_CONNECT_TIMEOUT_MS:3000}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.read-timeout-ms",
                "${XIYU_ORG_DIRECTORY_READ_TIMEOUT_MS:5000}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.auto-sync-menu-permissions",
                "${XIYU_ORG_AUTO_SYNC_MENU_PERMISSIONS:false}");
        assertPlaceholder(properties, "xiyu.integrations.organization.directory.unmapped-menu-code-behavior",
                "${XIYU_ORG_UNMAPPED_MENU_BEHAVIOR:IGNORE}");
        assertPlaceholder(properties, "xiyu.integrations.organization.retry.enabled", "${XIYU_ORG_RETRY_ENABLED:true}");
        assertPlaceholder(properties, "xiyu.integrations.organization.retry.max-attempts",
                "${XIYU_ORG_RETRY_MAX_ATTEMPTS:5}");
        assertPlaceholder(properties, "xiyu.integrations.organization.retry.batch-size",
                "${XIYU_ORG_RETRY_BATCH_SIZE:50}");
        assertPlaceholder(properties, "xiyu.integrations.organization.retry.fixed-delay-ms",
                "${XIYU_ORG_RETRY_FIXED_DELAY_MS:60000}");
        assertPlaceholder(properties, "xiyu.integrations.organization.reconciliation.enabled",
                "${XIYU_ORG_RECONCILIATION_ENABLED:false}");
        assertPlaceholder(properties, "xiyu.integrations.organization.reconciliation.cron",
                "${XIYU_ORG_RECONCILIATION_CRON:0 30 2 * * *}");
        assertPlaceholder(properties, "xiyu.integrations.organization.reconciliation.lookback-days",
                "${XIYU_ORG_RECONCILIATION_LOOKBACK_DAYS:3}");
    }

    @Test
    void developmentCorsAllowsTheLocalFrontendOrigins() {
        Properties properties = defaultProperties();

        assertThat(properties.getProperty("cors.allowed-origins"))
                .contains("http://localhost:1314")
                .contains("http://127.0.0.1:1314");
    }

    @Test
    void defaultApplicationProfileAndDatasourceUseMysql8() {
        Properties properties = defaultProperties();

        // Security: spring.profiles.active must fail closed when SPRING_PROFILES_ACTIVE is unset.
        // The default MUST NOT be "dev,mysql" — that would silently activate dev tooling
        // (h2-console, swagger, dev-only dataset seeders) on hosts that omit the env var.
        // Operators must explicitly opt-in by setting SPRING_PROFILES_ACTIVE.
        assertThat(properties.getProperty("spring.profiles.active")).isEqualTo("${SPRING_PROFILES_ACTIVE:}");
        assertThat(properties.getProperty("spring.profiles.active"))
                .as("spring.profiles.active default must not silently activate dev profile")
                .doesNotContain("dev")
                .doesNotContain("mysql");
        assertThat(properties.getProperty("spring.datasource.url")).contains("jdbc:mysql://");
        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.dialect"))
                .isEqualTo("org.hibernate.dialect.MySQLDialect");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration-mysql");
    }

    private static Properties productionProperties() {
        return loadProperties("application-prod.yml");
    }

    private static Properties defaultProperties() {
        return loadProperties("application.yml");
    }

    private static Properties mergedProductionProperties() {
        return loadProperties("application.yml", "application-prod.yml");
    }

    private static Properties loadProperties(String... resourceNames) {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        ClassPathResource[] resources = new ClassPathResource[resourceNames.length];
        for (int index = 0; index < resourceNames.length; index++) {
            resources[index] = new ClassPathResource(resourceNames[index]);
        }
        factoryBean.setResources(resources);
        Properties properties = factoryBean.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    private static void assertPlaceholder(Properties properties, String key, String expected) {
        assertThat(properties.getProperty(key)).as(key).isEqualTo(expected);
    }
}
