// Input: versionhistory service and request DTOs
// Output: Document Version REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.versionhistory.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.versionhistory.dto.DocumentVersionDTO;
import com.xiyu.bid.versionhistory.dto.VersionCreateRequest;
import com.xiyu.bid.versionhistory.dto.VersionDiffDTO;
import com.xiyu.bid.versionhistory.service.VersionHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档版本控制器
 * 提供文档版本管理的REST API端点
 */
@RestController
@RequestMapping("/api/documents/{projectId}/versions")
@RequiredArgsConstructor
@Slf4j
@Validated
public class DocumentVersionController {

    private final VersionHistoryService versionHistoryService;

    /**
     * 获取项目的所有版本
     * GET /api/documents/{projectId}/versions
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentVersionDTO>>> getVersionsByProject(
            @PathVariable Long projectId) {

        if (projectId == null || projectId <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid project ID"));
        }

        List<DocumentVersionDTO> versions = versionHistoryService.getVersionsByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * 获取项目的最新版本
     * GET /api/documents/{projectId}/versions/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<DocumentVersionDTO>> getLatestVersion(
            @PathVariable Long projectId) {

        if (projectId == null || projectId <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid project ID"));
        }

        DocumentVersionDTO version = versionHistoryService.getLatestVersion(projectId);
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    /**
     * 获取指定版本
     * GET /api/documents/{projectId}/versions/{versionId}
     */
    @GetMapping("/{versionId}")
    public ResponseEntity<ApiResponse<DocumentVersionDTO>> getVersion(
            @PathVariable Long projectId,
            @PathVariable Long versionId) {

        if (versionId == null || versionId <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid version ID"));
        }

        DocumentVersionDTO version = versionHistoryService.getVersion(projectId, versionId);
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    /**
     * 创建新版本
     * POST /api/documents/{projectId}/versions
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DocumentVersionDTO>> createVersion(
            @PathVariable Long projectId,
            @Valid @RequestBody VersionCreateRequest request) {

        // 验证项目ID匹配
        if (!request.getProjectId().equals(projectId)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Project ID in path does not match request body"));
        }

        DocumentVersionDTO version = versionHistoryService.createVersion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Version created successfully", version));
    }

    /**
     * 比较两个版本的差异
     * GET /api/documents/{projectId}/versions/{v1}/compare/{v2}
     */
    @GetMapping("/{v1}/compare/{v2}")
    public ResponseEntity<ApiResponse<VersionDiffDTO>> compareVersions(
            @PathVariable Long projectId,
            @PathVariable Long v1,
            @PathVariable Long v2) {

        if (v1 == null || v1 <= 0 || v2 == null || v2 <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid version IDs"));
        }

        VersionDiffDTO diff = versionHistoryService.compareVersions(projectId, v1, v2);
        return ResponseEntity.ok(ApiResponse.success(diff));
    }

    /**
     * 回滚到指定版本
     * POST /api/documents/{projectId}/versions/{versionId}/rollback
     */
    @PostMapping("/{versionId}/rollback")
    public ResponseEntity<ApiResponse<DocumentVersionDTO>> rollbackToVersion(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @RequestParam(required = false) Long userId) {

        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("User ID is required"));
        }

        DocumentVersionDTO version = versionHistoryService.rollbackToVersion(projectId, versionId, userId);
        return ResponseEntity.ok(ApiResponse.success("Rolled back successfully", version));
    }
}
