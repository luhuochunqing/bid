package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.application.OrganizationEventRetryAppService;
import com.xiyu.bid.integration.organization.application.OrganizationOperationsAppService;
import com.xiyu.bid.integration.organization.application.OrganizationOperationsStatusResponse;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/integrations/organization/operations")
@RequiredArgsConstructor
public class OrganizationOperationsController {
    private final OrganizationOperationsAppService operationsAppService;
    private final OrganizationEventRetryAppService retryAppService;

    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public OrganizationOperationsStatusResponse status() {
        return operationsAppService.status();
    }

    @PostMapping("/dead-letters/{eventKey}/replay")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public OrganizationEventWebhookResponse replayDeadLetter(@PathVariable String eventKey) {
        return retryAppService.replayDeadLetter(eventKey, LocalDateTime.now());
    }
}
