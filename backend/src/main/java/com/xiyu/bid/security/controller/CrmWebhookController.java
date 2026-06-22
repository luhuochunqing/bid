package com.xiyu.bid.security.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.webhook.CrmPermissionWebhookPayload;
import com.xiyu.bid.security.service.CrmPermissionSyncService;
import com.xiyu.bid.security.service.WebhookTokenAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/crm")
@PreAuthorize("permitAll()")
@RequiredArgsConstructor
@Slf4j
public class CrmWebhookController {

    private final CrmPermissionSyncService syncService;
    private final WebhookTokenAuthenticator tokenAuthenticator;

    @PostMapping("/permissions")
    public ResponseEntity<ApiResponse<Void>> syncPermissions(
            @RequestHeader(value = WebhookTokenAuthenticator.CRM_HEADER, required = false) String token,
            @RequestBody CrmPermissionWebhookPayload payload) {
        log.info("CRM permission webhook received: payload={}", payload);
        if (!tokenAuthenticator.isValidCrmToken(token)) {
            log.warn("CRM permission webhook rejected: invalid or missing token, customerId={}",
                    payload != null ? payload.getCustomerId() : "null");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid webhook token"));
        }
        log.info("CRM permission webhook token OK, syncing customerId={}", payload.getCustomerId());
        try {
            syncService.syncCustomerPermissions(payload);
            log.info("CRM permission webhook sync succeeded: customerId={}, permissionCount={}",
                    payload.getCustomerId(), payload.getPermissions() != null ? payload.getPermissions().size() : 0);
            return ResponseEntity.ok(ApiResponse.success("CRM permissions synced successfully", null));
        } catch (RuntimeException ex) {
            log.error("CRM permission webhook sync failed: customerId={}, payload={}",
                    payload.getCustomerId(), payload, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("CRM permission sync failed: " + ex.getMessage()));
        }
    }
}
