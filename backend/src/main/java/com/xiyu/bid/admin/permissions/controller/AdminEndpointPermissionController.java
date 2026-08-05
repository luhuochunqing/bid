// Input: endpoint permission catalog application service
// Output: admin-only endpoint permission matrix API
// Pos: admin permissions controller
package com.xiyu.bid.admin.permissions.controller;

import com.xiyu.bid.admin.permissions.application.EndpointPermissionCatalogAppService;
import com.xiyu.bid.admin.permissions.dto.EndpointPermissionItem;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminEndpointPermissionController {
    private static final String ENDPOINT_ETAG = "W/\"co605-endpoints-v1\"";
    private final EndpointPermissionCatalogAppService catalogAppService;

    @GetMapping("/endpoints")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<EndpointPermissionItem>>> listEndpointPermissions(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch
    ) {
        // CO-605: 接口权限矩阵在应用启动后不变（@PostConstruct 缓存），用固定 ETag 让浏览器命中 304。
        // 实体内容变化时重启应用即可（Controller 不会热加载），ETag 固定为应用版本标识。
        if (ENDPOINT_ETAG.equals(ifNoneMatch)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_MODIFIED)
                    .eTag(ENDPOINT_ETAG)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .build();
        }
        return ResponseEntity
                .ok()
                .eTag(ENDPOINT_ETAG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(ApiResponse.success(
                        "Endpoint permissions retrieved successfully",
                        catalogAppService.listEndpointPermissions()
                ));
    }
}
