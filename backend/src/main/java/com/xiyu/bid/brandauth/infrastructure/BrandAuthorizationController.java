package com.xiyu.bid.brandauth.infrastructure;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.brandauth.application.command.BrandAuthUpsertCommand;
import com.xiyu.bid.brandauth.application.dto.BrandAuthorizationDTO;
import com.xiyu.bid.brandauth.application.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Deprecated // replaced by manufacturer.ManufacturerAuthorizationController
// @RestController
// @RequestMapping("/api/knowledge/brand-auth")
@RequiredArgsConstructor
public class BrandAuthorizationController {

    private final CreateBrandAuthAppService createService;
    private final UpdateBrandAuthAppService updateService;
    private final DeleteBrandAuthAppService deleteService;
    private final ListBrandAuthAppService listService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "BrandAuthorization", description = "创建品牌授权")
    public ResponseEntity<ApiResponse<BrandAuthorizationDTO>> create(@Valid @RequestBody BrandAuthUpsertCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("品牌授权创建成功", createService.create(command)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "BrandAuthorization", description = "获取品牌授权列表")
    public ResponseEntity<ApiResponse<List<BrandAuthorizationDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("品牌授权列表获取成功", listService.list()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "BrandAuthorization", description = "获取品牌授权详情")
    public ResponseEntity<ApiResponse<BrandAuthorizationDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("品牌授权详情获取成功", listService.get(id)));
    }

    @GetMapping("/brand/{brandName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BrandAuthorizationDTO>>> byBrand(@PathVariable String brandName) {
        return ResponseEntity.ok(ApiResponse.success("按品牌查询成功", listService.byBrand(brandName)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "BrandAuthorization", description = "更新品牌授权")
    public ResponseEntity<ApiResponse<BrandAuthorizationDTO>> update(@PathVariable Long id,
            @Valid @RequestBody BrandAuthUpsertCommand command) {
        return ResponseEntity.ok(ApiResponse.success("品牌授权更新成功", updateService.update(id, command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "BrandAuthorization", description = "删除品牌授权")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
