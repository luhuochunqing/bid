package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.application.OrganizationSyncRunAppService;
import com.xiyu.bid.integration.organization.dto.OrganizationSyncRunRequest;
import com.xiyu.bid.integration.organization.dto.OrganizationSyncRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/integrations/organization/sync-runs")
@RequiredArgsConstructor
public class OrganizationSyncRunController {
    private static final String DEFAULT_SOURCE_APP = "oss";
    private static final String DEFAULT_RUN_TYPE = "RECONCILIATION";

    private final OrganizationSyncRunAppService syncRunAppService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public OrganizationSyncRunResponse startSyncRun(@RequestBody(required = false) OrganizationSyncRunRequest request) {
        LocalDateTime endAt = request == null || request.endAt() == null ? LocalDateTime.now() : request.endAt();
        LocalDateTime startAt = request == null || request.startAt() == null ? endAt.minusDays(3) : request.startAt();
        return OrganizationSyncRunResponse.from(syncRunAppService.syncWindow(
                sourceApp(request),
                startAt,
                endAt,
                runType(request)
        ));
    }

    private String sourceApp(OrganizationSyncRunRequest request) {
        if (request == null || request.sourceApp() == null || request.sourceApp().isBlank()) {
            return DEFAULT_SOURCE_APP;
        }
        return request.sourceApp().trim();
    }

    private String runType(OrganizationSyncRunRequest request) {
        if (request == null || request.runType() == null || request.runType().isBlank()) {
            return DEFAULT_RUN_TYPE;
        }
        return request.runType().trim().toUpperCase(Locale.ROOT);
    }
}
