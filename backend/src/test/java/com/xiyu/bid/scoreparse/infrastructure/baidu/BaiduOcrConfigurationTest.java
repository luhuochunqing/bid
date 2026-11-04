package com.xiyu.bid.scoreparse.infrastructure.baidu;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class BaiduOcrConfigurationTest {
    @Test void scoreParsingUsesSharedCredentialsWhenNoDedicatedOverrideExists() throws Exception {
        var environment = environment(Map.of("app.baidu-ocr.api-key", "shared-key",
                "app.baidu-ocr.secret-key", "shared-secret"));
        // Boolean assertions avoid printing packaged credentials if the regression returns.
        assertThat("shared-key".equals(environment.getProperty("app.score-parse.baidu-ocr.api-key"))).isTrue();
        assertThat("shared-secret".equals(environment.getProperty("app.score-parse.baidu-ocr.secret-key"))).isTrue();
    }

    @Test void dedicatedEnvironmentOverridesRemainEffective() throws Exception {
        var environment = environment(Map.of("APP_SCORE_PARSE_BAIDU_OCR_API_KEY", "dedicated-key",
                "APP_SCORE_PARSE_BAIDU_OCR_SECRET_KEY", "dedicated-secret",
                "BAIDU_OCR_API_KEY", "shared-key", "BAIDU_OCR_SECRET_KEY", "shared-secret"));
        assertThat("dedicated-key".equals(environment.getProperty("app.score-parse.baidu-ocr.api-key"))).isTrue();
        assertThat("dedicated-secret".equals(environment.getProperty("app.score-parse.baidu-ocr.secret-key"))).isTrue();
    }

    private static StandardEnvironment environment(Map<String, Object> overrides) throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
            environment.getPropertySources().addLast(source);
        return environment;
    }
}
