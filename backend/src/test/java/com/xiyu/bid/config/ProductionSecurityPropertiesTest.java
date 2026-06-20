package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityPropertiesTest {

    @Test
    void productionErrorExposureIsTightened() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = factoryBean.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(properties.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        assertThat(properties.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }

    @Test
    void productionKeepsSensitiveSettingsExternalized() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = factoryBean.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("jwt.secret")).isEqualTo("${JWT_SECRET}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(properties.getProperty("app.bootstrap.admin.password")).isEqualTo("${ADMIN_PASSWORD}");
        assertThat(properties.getProperty("cors.allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS}");
    }

    @Test
    void productionDatasourceDefaultsToMysql8() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = factoryBean.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.datasource.url")).contains("jdbc:mysql://");
        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.dialect"))
                .isEqualTo("org.hibernate.dialect.MySQLDialect");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration-mysql");
    }

    @Test
    void productionProfileKeepsCrmBindingsExternalized() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application.yml"), new ClassPathResource("application-prod.yml"));
        Properties properties = factoryBean.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("app.crm.auth-base-url")).isEqualTo("${XIYU_CRM_AUTH_BASE_URL:}");
        assertThat(properties.getProperty("app.crm.base-url")).isEqualTo("${XIYU_CRM_BASE_URL:}");
        assertThat(properties.getProperty("app.crm.chance-base-url")).isEqualTo("${XIYU_CRM_CHANCE_BASE_URL:}");
        assertThat(properties.getProperty("app.crm.oauth-username")).isEqualTo("${XIYU_CRM_OAUTH_USERNAME:}");
        assertThat(properties.getProperty("app.crm.oauth-password")).isEqualTo("${XIYU_CRM_OAUTH_PASSWORD:}");
        assertThat(properties.getProperty("app.crm.generate-token-nick-name"))
                .isEqualTo("${XIYU_CRM_GENERATE_TOKEN_NICK_NAME:}");
        assertThat(properties.getProperty("app.crm.generate-token-sales-no"))
                .isEqualTo("${XIYU_CRM_GENERATE_TOKEN_SALES_NO:}");
        assertThat(properties.getProperty("app.crm.auth.oauth-login-path"))
                .isEqualTo("${XIYU_CRM_AUTH_OAUTH_LOGIN_PATH:/oauth/login}");
    }

    @Test
    void developmentCorsAllowsTheLocalFrontendOrigins() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application.yml"));
        Properties properties = factoryBean.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("cors.allowed-origins"))
                .contains("http://localhost:1314")
                .contains("http://127.0.0.1:1314");
    }

    @Test
    void defaultApplicationProfileAndDatasourceUseMysql8() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application.yml"));
        Properties properties = factoryBean.getObject();

        assertThat(properties).isNotNull();
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
}
