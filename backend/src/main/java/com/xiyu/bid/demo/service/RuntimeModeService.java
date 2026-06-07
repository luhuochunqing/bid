package com.xiyu.bid.demo.service;

import com.xiyu.bid.dto.RuntimeModeResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class RuntimeModeService {

    private final Environment environment;
    private final DemoModeService demoModeService;

    public RuntimeModeService(Environment pEnvironment, DemoModeService pDemoModeService) {
        this.environment = pEnvironment;
        this.demoModeService = pDemoModeService;
    }

    public RuntimeModeResponse getCurrentMode() {
        List<String> activeProfiles = resolveProfiles();
        boolean demoFusionEnabled = demoModeService.isEnabled();
        String database = detectDatabaseKind();
        String modeCode = buildModeCode(activeProfiles, demoFusionEnabled);
        String modeLabel = buildModeLabel(modeCode);

        return RuntimeModeResponse.builder()
                .modeCode(modeCode)
                .modeLabel(modeLabel)
                .database(database)
                .demoFusionEnabled(demoFusionEnabled)
                .activeProfiles(activeProfiles)
                .build();
    }

    private List<String> resolveProfiles() {
        String[] active = environment.getActiveProfiles();
        if (active != null && active.length > 0) {
            return Arrays.stream(active).toList();
        }
        String[] defaults = environment.getDefaultProfiles();
        return defaults == null ? List.of() : Arrays.stream(defaults).toList();
    }

    private String detectDatabaseKind() {
        String url = environment.getProperty("spring.datasource.url", "");
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(":h2:")) {
            return "h2";
        }
        if (lower.contains(":mysql:")) {
            return "mysql";
        }
        return "unknown";
    }

    private String buildModeCode(List<String> profiles, boolean demoFusionEnabled) {
        if (demoFusionEnabled) {
            return "e2e_demo_fusion";
        }
        if (profiles.stream().anyMatch(profile -> "mysql".equalsIgnoreCase(profile))) {
            return "real_api_mysql";
        }
        return "real_api";
    }

    private String buildModeLabel(String modeCode) {
        return switch (modeCode) {
            case "e2e_demo_fusion" -> "E2E/H2（真实 + Demo 融合）";
            case "real_api_mysql" -> "真实 API（MySQL）";
            default -> "真实 API";
        };
    }
}
