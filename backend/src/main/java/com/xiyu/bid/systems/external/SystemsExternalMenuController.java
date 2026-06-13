package com.xiyu.bid.systems.external;

import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部系统菜单接口.
 *
 * <p>供统一组织架构系统通过 <code>/api/systems/external/menus</code>
 * 拉取本系统的菜单树，用于菜单权限配置。</p>
 *
 * <p>接口无需认证，已由 SecurityConfig 白名单放行。</p>
 */
@RestController
@RequestMapping("/api/systems/external/menus")
public class SystemsExternalMenuController {

    /** 外部菜单服务. */
    private final ExternalMenuService menuService;

    /**
     * 构造控制器.
     *
     * @param service 外部菜单服务
     */
    public SystemsExternalMenuController(final ExternalMenuService service) {
        this.menuService = service;
    }

    /**
     * 获取本系统菜单树.
     *
     * @return 菜单树列表（按客户方规范包成 ExternalMenuResponse 暴露）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ExternalMenuResponse>> getMenus() {
        
        ApiResponse<ExternalMenuResponse> resp = ApiResponse
                .<ExternalMenuResponse>builder()
                .success(true)
                .code(0)
                .message("ok")
                .data(menuService.getMenus())
                .build();
        return ResponseEntity.ok(resp);
    }
}
