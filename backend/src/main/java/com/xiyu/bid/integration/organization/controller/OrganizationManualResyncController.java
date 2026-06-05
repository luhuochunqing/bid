package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.application.OrganizationManualResyncAppService;
import com.xiyu.bid.integration.organization.dto.OrganizationSyncRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/integrations/organization/resync")
@RequiredArgsConstructor
public class OrganizationManualResyncController {
    private static final String DEFAULT_SOURCE_APP = "oss";

    private final OrganizationManualResyncAppService manualResyncAppService;

    @PostMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public OrganizationSyncRunResponse resyncUser(@PathVariable String userId, Principal principal) {
        return OrganizationSyncRunResponse.from(
                manualResyncAppService.resyncUser(DEFAULT_SOURCE_APP, userId, operator(principal))
        );
    }

    @PostMapping("/departments/{deptId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public OrganizationSyncRunResponse resyncDepartment(@PathVariable String deptId, Principal principal) {
        return OrganizationSyncRunResponse.from(
                manualResyncAppService.resyncDepartment(DEFAULT_SOURCE_APP, deptId, operator(principal))
        );
    }

    private String operator(Principal principal) {
        return principal == null || principal.getName() == null || principal.getName().isBlank()
                ? "system"
                : principal.getName();
    }
}
