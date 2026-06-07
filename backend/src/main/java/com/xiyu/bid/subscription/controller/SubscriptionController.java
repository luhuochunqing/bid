package com.xiyu.bid.subscription.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.PageDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.subscription.core.SubscriptionPolicy;
import com.xiyu.bid.subscription.core.SubscriptionPolicy.ValidationResult;
import com.xiyu.bid.subscription.dto.SubscriptionRequest;
import com.xiyu.bid.subscription.dto.SubscriptionSummary;
import com.xiyu.bid.subscription.service.SubscriptionApplicationService;
import com.xiyu.bid.subscription.service.SubscriptionApplicationService.SubscribeResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class SubscriptionController {

    private static final int MAX_PAGE_SIZE = 100;
    private final SubscriptionApplicationService service;
    private final AuthService authService;

    public SubscriptionController(SubscriptionApplicationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subscribe(
        @Valid @RequestBody SubscriptionRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        SubscribeResult result = service.subscribe(
            currentUser.getId(), request.targetEntityType(), request.targetEntityId());
        if (!result.success()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error(400, result.errorMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success("Subscribed",
            Map.of("subscriptionId", result.subscriptionId())));
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unsubscribe(
        @Valid @RequestBody SubscriptionRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        int affected = service.unsubscribe(
            currentUser.getId(), request.targetEntityType(), request.targetEntityId());
        return ResponseEntity.ok(ApiResponse.success("Unsubscribed",
            Map.of("affected", affected)));
    }

    @GetMapping("/subscriptions/me")
    public ResponseEntity<ApiResponse<PageDTO<SubscriptionSummary>>> listMine(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        User currentUser = resolveCurrentUser(userDetails);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<SubscriptionSummary> result = service.listMySubscriptions(
            currentUser.getId(), PageRequest.of(safePage, safeSize));
        PageDTO<SubscriptionSummary> pageDTO = PageDTO.of(
            result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
        return ResponseEntity.ok(ApiResponse.success("Subscriptions retrieved", pageDTO));
    }

    @GetMapping("/entities/{entityType}/{entityId}/subscription")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkSubscribed(
        @PathVariable String entityType,
        @PathVariable Long entityId,
        @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        ValidationResult validation = SubscriptionPolicy.validate(currentUser.getId(), entityType, entityId);
        if (!validation.isValid()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.error(400, validation.errorMessage()));
        }
        boolean subscribed = service.isSubscribed(currentUser.getId(), entityType, entityId);
        return ResponseEntity.ok(ApiResponse.success("Check result",
            Map.of("subscribed", subscribed)));
    }

    private User resolveCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new AuthenticationServiceException("UserDetails cannot be null");
        }
        String username = userDetails.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationServiceException("Username cannot be null or empty");
        }
        try {
            return authService.resolveUserByUsername(username.trim());
        } catch (UsernameNotFoundException ex) {
            throw new AuthenticationServiceException("Authenticated user not found: " + username, ex);
        }
    }
}
