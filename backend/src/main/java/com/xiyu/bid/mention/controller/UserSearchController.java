package com.xiyu.bid.mention.controller;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.dto.UserSearchResult;
import com.xiyu.bid.mention.service.UserSearchService;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("isAuthenticated()")
@Validated
public class UserSearchController {

    private final UserSearchService searchService;

    public UserSearchController(UserSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
        @RequestParam(name = "q", defaultValue = "") @Size(max = 100) String q,
        @RequestParam(name = "limit", required = false) Integer limit,
        @AuthenticationPrincipal User currentUser) {
        List<UserSearchResult> data = searchService.search(q, limit);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }
}
