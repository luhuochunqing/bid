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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * CO-384: 批量按 ID 查询用户，用于前端补查已分配人员姓名。
     * 返回结构与 /api/users/search 一致，前端可复用 normalizeUserOption。
     */
    @GetMapping("/batch")
    public ResponseEntity<Map<String, Object>> batch(
        @RequestParam(name = "ids") String ids) {
        List<Long> idList = parseIds(ids);
        List<UserSearchResult> data = searchService.findByIds(idList);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }

    private static List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(s -> {
                try {
                    return Long.parseLong(s);
                } catch (NumberFormatException ex) {
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }
}
