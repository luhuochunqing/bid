// Input: documents service and request DTOs
// Output: Document Assembly REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.documents.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.documents.dto.AssemblyRequest;
import com.xiyu.bid.documents.dto.AssemblyTemplateDTO;
import com.xiyu.bid.documents.dto.DocumentAssemblyDTO;
import com.xiyu.bid.documents.dto.TemplateCreateRequest;
import com.xiyu.bid.documents.service.DocumentAssemblyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文档组装控制器
 * 处理文档组装相关的HTTP请求
 */
@RestController
@RequestMapping("/api/documents/assembly")
@RequiredArgsConstructor
@Slf4j
public class DocumentAssemblyController {

    private final DocumentAssemblyService documentAssemblyService;

    /**
     * 获取所有模板（支持按分类过滤）
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<AssemblyTemplateDTO>>> getTemplates(
            @RequestParam(required = false) String category) {

        log.info("GET /api/documents/assembly/templates - Fetching templates{}", category != null ? " by category: " + category : "");

        List<AssemblyTemplateDTO> templates;
        if (category != null && !category.isEmpty()) {
            templates = documentAssemblyService.getTemplatesByCategory(category);
        } else {
            // Return empty list if no category specified
            // To get all templates, you need to query by specific category
            templates = List.of();
        }

        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved templates", templates));
    }

    /**
     * 创建新模板
     */
    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AssemblyTemplateDTO>> createTemplate(
            @Valid @RequestBody TemplateCreateRequest request) {

        log.info("POST /api/documents/assembly/templates - Creating template: {}", request.getName());
        AssemblyTemplateDTO createdTemplate = documentAssemblyService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Template created successfully", createdTemplate));
    }

    /**
     * 获取项目的所有组装记录
     */
    @GetMapping("/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<DocumentAssemblyDTO>>> getAssembliesByProject(
            @PathVariable Long projectId) {

        log.info("GET /api/documents/assembly/{} - Fetching assemblies", projectId);
        List<DocumentAssemblyDTO> assemblies = documentAssemblyService.getAssembliesByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved assemblies", assemblies));
    }

    /**
     * 组装新文档
     */
    @PostMapping("/{projectId}/assemble")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentAssemblyDTO>> assembleDocument(
            @PathVariable Long projectId,
            @Valid @RequestBody AssemblyRequest request) {

        log.info("POST /api/documents/assembly/{}/assemble - Assembling document with template: {}",
                projectId, request.getTemplateId());

        DocumentAssemblyDTO assembly = documentAssemblyService.assembleDocument(
                projectId,
                request.getTemplateId(),
                request.getVariables(),
                request.getAssembledBy()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document assembled successfully", assembly));
    }

    /**
     * 重新生成组装文档
     */
    @PutMapping("/{id}/regenerate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentAssemblyDTO>> regenerateAssembly(
            @PathVariable Long id) {

        log.info("PUT /api/documents/assembly/{}/regenerate - Regenerating assembly", id);
        DocumentAssemblyDTO assembly = documentAssemblyService.regenerateAssembly(id);
        return ResponseEntity.ok(ApiResponse.success("Document regenerated successfully", assembly));
    }
}
