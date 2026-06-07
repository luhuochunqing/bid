// Input: REST requests for keyword subscription CRUD + match results
// Output: REST endpoints backed by resolved User entity ids
// Pos: Controller/标讯关键词订阅 REST 接口
package com.xiyu.bid.tenderkeyword.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.dto.PageDTO;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tenderkeyword.dto.CreateSubscriptionRequest;
import com.xiyu.bid.tenderkeyword.dto.MatchResultDTO;
import com.xiyu.bid.tenderkeyword.dto.SubscriptionDTO;
import com.xiyu.bid.tenderkeyword.dto.UpdateSubscriptionRequest;
import com.xiyu.bid.tenderkeyword.service.TenderKeywordSubscriptionService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class TenderKeywordSubscriptionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenderKeywordSubscriptionService service;
    private final AuthService authService;

    public TenderKeywordSubscriptionController(
            TenderKeywordSubscriptionService service,
            AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    /**
     * 创建关键词订阅
     */
    @PostMapping("/tender-keyword-subscriptions")
    public ResponseEntity<ApiResponse<SubscriptionDTO>> create(
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        SubscriptionDTO result = service.create(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("订阅创建成功", result));
    }

    /**
     * 查询我的订阅列表
     */
    @GetMapping("/tender-keyword-subscriptions")
    public ResponseEntity<ApiResponse<PageDTO<SubscriptionDTO>>> listMine(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User currentUser = resolveCurrentUser(userDetails);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<SubscriptionDTO> result = service.listByUser(
                currentUser.getId(), PageRequest.of(Math.max(page, 0), safeSize));
        PageDTO<SubscriptionDTO> pageDTO = PageDTO.of(
                result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
        return ResponseEntity.ok(ApiResponse.success("订阅列表", pageDTO));
    }

    /**
     * 查询单个订阅详情
     */
    @GetMapping("/tender-keyword-subscriptions/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDTO>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        return service.getById(id, currentUser.getId())
                .map(dto -> ResponseEntity.ok(ApiResponse.success("订阅详情", dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新订阅
     */
    @PutMapping("/tender-keyword-subscriptions/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        return service.update(id, currentUser.getId(), request)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("订阅更新成功", dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除订阅
     */
    @DeleteMapping("/tender-keyword-subscriptions/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        if (service.delete(id, currentUser.getId())) {
            return ResponseEntity.ok(ApiResponse.success("订阅删除成功", null));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 切换订阅状态（暂停/启用）
     */
    @PatchMapping("/tender-keyword-subscriptions/{id}/toggle")
    public ResponseEntity<ApiResponse<SubscriptionDTO>> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = resolveCurrentUser(userDetails);
        return service.toggleStatus(id, currentUser.getId())
                .map(dto -> ResponseEntity.ok(ApiResponse.success("状态切换成功", dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 查询我的匹配结果
     */
    @GetMapping("/tender-keyword-subscriptions/match-results")
    public ResponseEntity<ApiResponse<PageDTO<MatchResultDTO>>> listMatchResults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User currentUser = resolveCurrentUser(userDetails);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<MatchResultDTO> result = service.listMatchResults(
                currentUser.getId(), PageRequest.of(Math.max(page, 0), safeSize));
        PageDTO<MatchResultDTO> pageDTO = PageDTO.of(
                result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
        return ResponseEntity.ok(ApiResponse.success("匹配结果", pageDTO));
    }

    /**
     * 查询指定订阅的匹配结果
     */
    @GetMapping("/tender-keyword-subscriptions/{id}/match-results")
    public ResponseEntity<ApiResponse<PageDTO<MatchResultDTO>>> listMatchResultsBySubscription(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User currentUser = resolveCurrentUser(userDetails);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<MatchResultDTO> result = service.listMatchResultsBySubscription(
                id, currentUser.getId(), PageRequest.of(Math.max(page, 0), safeSize));
        PageDTO<MatchResultDTO> pageDTO = PageDTO.of(
                result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
        return ResponseEntity.ok(ApiResponse.success("订阅匹配结果", pageDTO));
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
