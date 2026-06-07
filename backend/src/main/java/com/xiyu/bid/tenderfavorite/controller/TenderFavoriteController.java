package com.xiyu.bid.tenderfavorite.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.tenderfavorite.dto.TenderFavoriteDTO;
import com.xiyu.bid.tenderfavorite.service.TenderFavoriteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 标讯收藏 REST API
 */
@RestController
@Tag(name = "标讯收藏", description = "标讯收藏与取消收藏操作")
@RequestMapping("/api/tender-favorites")
@RequiredArgsConstructor
@Slf4j
public class TenderFavoriteController {

    private final TenderFavoriteService tenderFavoriteService;
    private final AuthService authService;

    /**
     * 切换收藏状态（已收藏则取消，未收藏则添加）
     *
     * @param tenderId    标讯ID
     * @param userDetails 当前用户
     * @return { favorited: true/false }
     */
    @PostMapping("/{tenderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleFavorite(
            @PathVariable Long tenderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/tender-favorites/{} - Toggle favorite", tenderId);
        Long userId = resolveUserId(userDetails);
        boolean isFavorited = tenderFavoriteService.toggleFavorite(userId, tenderId);
        return ResponseEntity.ok(ApiResponse.success(
                isFavorited ? "收藏成功" : "已取消收藏",
                Map.of("favorited", isFavorited)
        ));
    }

    /**
     * 获取当前用户收藏的所有标讯ID列表
     *
     * @return { ids: [1, 2, 3] }
     */
    @GetMapping("/ids")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, List<Long>>>> getFavoriteIds(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/tender-favorites/ids - Get favorite IDs");
        Long userId = resolveUserId(userDetails);
        List<Long> ids = tenderFavoriteService.getFavoriteTenderIds(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ids", ids)));
    }

    /**
     * 分页获取当前用户的收藏标讯列表（含详情），按收藏时间降序
     *
     * @param page 页码（从0开始）
     * @param size 每页条数
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Page<TenderFavoriteDTO>>> getMyFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/tender-favorites - Get my favorites, page={}, size={}", page, size);
        Long userId = resolveUserId(userDetails);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TenderFavoriteDTO> result = tenderFavoriteService.getFavoriteTenders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("获取收藏列表成功", result));
    }

    /**
     * 取消收藏指定标讯
     */
    @DeleteMapping("/{tenderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable Long tenderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("DELETE /api/tender-favorites/{} - Remove favorite", tenderId);
        Long userId = resolveUserId(userDetails);
        tenderFavoriteService.removeFavorite(userId, tenderId);
        return ResponseEntity.ok(ApiResponse.success("已取消收藏", null));
    }

    /**
     * 检查用户是否已收藏某标讯
     */
    @GetMapping("/check/{tenderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkFavorited(
            @PathVariable Long tenderId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/tender-favorites/check/{} - Check if favorited", tenderId);
        Long userId = resolveUserId(userDetails);
        boolean isFavorited = tenderFavoriteService.isFavorited(userId, tenderId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("favorited", isFavorited)));
    }

    private Long resolveUserId(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null || userDetails.getUsername().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "无法识别当前用户");
        }
        return authService.resolveUserIdByUsername(userDetails.getUsername().trim());
    }
}
