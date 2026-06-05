package com.xiyu.bid.security.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.webhook.CrmPermissionWebhookPayload;
import com.xiyu.bid.security.service.CrmPermissionSyncService;
import com.xiyu.bid.security.service.WebhookTokenAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/crm")
@RequiredArgsConstructor
@Slf4j
public class CrmWebhookController {

    private final CrmPermissionSyncService syncService;
    private final WebhookTokenAuthenticator tokenAuthenticator;

    @PostMapping("/permissions")
    public ResponseEntity<ApiResponse<Void>> syncPermissions(
            @RequestHeader(value = WebhookTokenAuthenticator.CRM_HEADER, required = false) String token,
            @RequestBody CrmPermissionWebhookPayload payload) {
        if (!tokenAuthenticator.isValidCrmToken(token)) {
            log.warn("CRM permission webhook rejected: invalid or missing token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid webhook token"));
        }
        log.info("Received CRM permission sync request for customer: {}", payload.getCustomerId());
        syncService.syncCustomerPermissions(payload);
        return ResponseEntity.ok(ApiResponse.success("CRM permissions synced successfully", null));
    }
}
