package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.settings.service.AiConfigService;
import com.xiyu.bid.settings.service.AiProviderCatalog;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScoreExtractionRequestBudgetTest {
    @Test void scoringBudgetIsIndependentOfOtherAiRequests() {
        var settings = mock(AiConfigService.class);
        var env = mock(Environment.class);
        when(settings.resolveAiApiKey("deepseek")).thenReturn("diagnostic-only");
        var resolver = new OpenAiBidAgentConfigurationResolver(settings, new AiProviderCatalog(), env,
                Duration.ofSeconds(30), Duration.ofSeconds(90));
        assertThat(resolver.resolve("multimodal score fact extraction").timeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(resolver.resolve("multimodal score fact extraction").maxRetries()).isZero();
        assertThat(resolver.resolve("bid draft generation").maxRetries()).isEqualTo(2);
        assertThat(resolver.resolve("bid draft generation").timeout()).isEqualTo(Duration.ofSeconds(90));
    }
    @Test void springBindsIndependentTimeoutOverrideWithoutChangingOtherUseCases() {
        configuredContext("PT17S").run(context -> {
            var resolver = context.getBean(OpenAiBidAgentConfigurationResolver.class);
            assertThat(resolver.resolve("multimodal score fact extraction").timeout()).isEqualTo(Duration.ofSeconds(17));
            assertThat(resolver.resolve("multimodal score fact extraction").maxRetries()).isZero();
            assertThat(resolver.resolve("bid draft generation").timeout()).isEqualTo(Duration.ofSeconds(90));
        });
    }
    @Test void nonPositiveScoringBudgetFailsBeforeCallingProvider() {
        configuredContext("PT0S").run(context -> org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> context.getBean(OpenAiBidAgentConfigurationResolver.class)
                        .resolve("multimodal score fact extraction"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("AI request timeout must be positive"));
    }
    private org.springframework.boot.test.context.runner.ApplicationContextRunner configuredContext(String timeout) {
        return new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
                .withBean(AiConfigService.class, () -> {
                    var service = mock(AiConfigService.class);
                    when(service.resolveAiApiKey("deepseek")).thenReturn("diagnostic-only");
                    return service;
                }).withBean(AiProviderCatalog.class).withBean(OpenAiBidAgentConfigurationResolver.class)
                .withPropertyValues("app.score-parse.model.timeout=" + timeout);
    }

}
