package com.xiyu.bid;

import com.xiyu.maintenance.CaseProvenancePreviewApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class XiyuBidApplication {

    public static void main(String[] args) {
        // CO-438: 无显示器 Linux 服务器上 AWT 字体系统初始化需要 headless 模式，
        // 否则 POI autoSizeColumn 会报 "Fontconfig head is null"（systemd 默认不设此参数）
        System.setProperty("java.awt.headless", "true");
        Class<?> applicationClass = applicationClassFor(args);
        SpringApplication application = new SpringApplication(applicationClass);
        if (applicationClass == CaseProvenancePreviewApplication.class) {
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setLazyInitialization(true);
            application.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("caseProvenancePreviewSafety", previewSafetyDefaults())));
        }
        application.run(args);
    }

    static Class<?> applicationClassFor(String[] args) {
        for (String arg : args) {
            if ("--case-provenance-backfill=preview".equals(arg)) {
                return CaseProvenancePreviewApplication.class;
            }
        }
        return XiyuBidApplication.class;
    }

    static Map<String, Object> previewSafetyDefaults() {
        return Map.ofEntries(
                Map.entry("spring.main.web-application-type", "none"),
                Map.entry("spring.main.lazy-initialization", "true"),
                Map.entry("spring.flyway.enabled", "false"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "none"),
                Map.entry("spring.jpa.generate-ddl", "false"),
                Map.entry("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none"),
                Map.entry("spring.jpa.properties.hibernate.hbm2ddl.auto", "none"),
                Map.entry("spring.datasource.hikari.read-only", "true"),
                Map.entry("spring.task.scheduling.enabled", "false"),
                Map.entry("xiyu.integrations.organization.event-sdk.enabled", "false"),
                Map.entry("xiyu.integrations.tender-event.sdk.enabled", "false"),
                Map.entry("xiyu.opportunity-history-replay.enabled", "false"));
    }
}
