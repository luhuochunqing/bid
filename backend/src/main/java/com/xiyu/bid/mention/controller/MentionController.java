// Input: authenticated UserDetails + @ mention create requests
// Output: mention creation REST endpoint, resolves principal to User entity
// Pos: mention/controller - REST 边界层
package com.xiyu.bid.mention.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.service.MentionApplicationService;
import com.xiyu.bid.mention.service.MentionApplicationService.MentionResult;
import com.xiyu.bid.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mentions")
@PreAuthorize("isAuthenticated()")
public class MentionController {

    private final MentionApplicationService service;
    private final AuthService authService;

    public MentionController(MentionApplicationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        @Valid @RequestBody CreateMentionRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        MentionResult result = service.createMention(request, currentUser.getId());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "mentionCount", result.mentionCount(),
                "notificationId", result.notificationId() == null ? 0L : result.notificationId()
            )
        ));
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
