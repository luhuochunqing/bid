package com.xiyu.bid.apikey.infrastructure;

import com.xiyu.bid.apikey.application.ApiKeyService;
import com.xiyu.bid.apikey.dto.ApiKeyResponse;
import com.xiyu.bid.apikey.dto.CreateApiKeyRequest;
import com.xiyu.bid.apikey.dto.CreateApiKeyResponse;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/admin/api-keys")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "API Key 管理", description = "第三方接入 API Key 的创建与生命周期管理")
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> listAll() {
        List<ApiKeyResponse> keys = apiKeyService.listAll().stream()
                .map(ApiKeyResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("API Keys retrieved", keys));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> create(
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication auth) {
        String createdBy = auth != null ? auth.getName() : "system";
        ApiKeyService.CreateResult result = apiKeyService.create(
                request.name(), request.scopes(), createdBy, request.expiresAt());
        CreateApiKeyResponse resp = new CreateApiKeyResponse(
                result.id(), result.secret(), result.name(), result.scopes(), result.expiresAt(),
                "请立即保存此 Secret，关闭后无法再次获取");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("API Key created", resp));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disable(@PathVariable Long id) {
        apiKeyService.disable(id);
        return ResponseEntity.ok(ApiResponse.success("API Key disabled", null));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Void>> enable(@PathVariable Long id) {
        apiKeyService.enable(id);
        return ResponseEntity.ok(ApiResponse.success("API Key enabled", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        apiKeyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("API Key deleted", null));
    }
}
