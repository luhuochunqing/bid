package com.xiyu.bid.crm.controller;

import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OSS 权限诊断与管理端点。
 * <p>
 * 提供以下能力：
 * <ol>
 *   <li>诊断：查看指定用户的 OSS 权限缓存状态、roleCode、menuPermissions、过期时间</li>
 *   <li>刷新：清除指定用户的 OSS 权限缓存，让用户下次登录时重新从 OSS 抓取</li>
 * </ol>
 * <p>
 * 仅限系统管理员访问（{@code system.admin} 权限）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/oss-permission")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION + "')")
public class OssPermissionDiagnosisController {

    private final OssPermissionCache ossPermissionCache;

    /**
     * 诊断：查看指定用户的 OSS 权限缓存状态。
     * <p>
     * 返回缓存命中/缺失、roleCode、menuPermissions 列表、缓存过期时间等信息，
     * 帮助运维人员排查"用户菜单不显示"类问题。
     *
     * @param username 目标用户名（工号）
     * @return 缓存诊断信息
     */
    @GetMapping("/diagnosis/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diagnose(@PathVariable String username) {
        log.info("GET /api/admin/oss-permission/diagnosis/{} - diagnosing OSS permission cache", username);

        Optional<OssPermissionCache.CacheEntry> entry = ossPermissionCache.getEntry(username);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", username);
        result.put("cacheHit", entry.isPresent());

        if (entry.isPresent()) {
            OssPermissionCache.CacheEntry cached = entry.get();
            result.put("roleCode", cached.roleCode());
            result.put("menuPermissions", cached.menuPermissions());
            result.put("menuPermissionCount", cached.menuPermissions() != null ? cached.menuPermissions().size() : 0);
            result.put("expiresAt", cached.expiresAt().toString());
            result.put("ttlRemainingSeconds", Math.max(0,
                    cached.expiresAt().getEpochSecond() - Instant.now().getEpochSecond()));
            // 不暴露原始 OSS permission 对象的全部细节（可能含敏感信息），只暴露结构摘要
            result.put("hasRawOssPermission", cached.permission() != null);
        } else {
            result.put("roleCode", null);
            result.put("menuPermissions", List.of());
            result.put("menuPermissionCount", 0);
            result.put("hint", "缓存为空，用户需要重新登录以从 OSS 抓取最新权限");
        }

        return ResponseEntity.ok(ApiResponse.success("OSS permission diagnosis", result));
    }

    /**
     * 刷新（清除缓存）：清除指定用户的 OSS 权限缓存。
     * <p>
     * 清除后，用户下次登录将从 OSS 实时抓取最新权限（而非使用旧缓存）。
     * <p>
     * 注意：此端点仅清除缓存，不会主动触发 OSS 抓取（因为需要用户密码）。
     * 管理员应通知用户重新登录以完成权限刷新。
     *
     * @param username 目标用户名（工号）
     * @return 操作结果
     */
    @DeleteMapping("/cache/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> invalidateCache(@PathVariable String username) {
        log.info("DELETE /api/admin/oss-permission/cache/{} - invalidating OSS permission cache", username);

        // 先检查缓存是否存在，方便返回有意义的信息
        boolean hadCache = ossPermissionCache.getEntry(username).isPresent();

        ossPermissionCache.invalidate(username);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", username);
        result.put("previousCacheExisted", hadCache);
        result.put("cacheCleared", true);
        result.put("nextStep", "请通知用户重新登录，系统将从 OSS 重新抓取最新权限");

        log.info("OSS permission cache invalidated for user={}, hadPreviousCache={}", username, hadCache);

        return ResponseEntity.ok(ApiResponse.success(
                hadCache ? "OSS 权限缓存已清除，用户需重新登录" : "该用户无缓存记录，无需清除",
                result));
    }
}
