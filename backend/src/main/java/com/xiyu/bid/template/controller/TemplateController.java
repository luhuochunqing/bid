// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.template.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.template.dto.TemplateCopyRequest;
import com.xiyu.bid.template.dto.TemplateDTO;
import com.xiyu.bid.template.dto.TemplateDownloadRecordRequest;
import com.xiyu.bid.template.dto.TemplateMutationRequest;
import com.xiyu.bid.template.dto.TemplateUseRecordDTO;
import com.xiyu.bid.template.dto.TemplateUseRecordRequest;
import com.xiyu.bid.template.dto.TemplateVersionDTO;
import com.xiyu.bid.template.service.TemplateService;
import com.xiyu.bid.templatecatalog.application.command.TemplateQueryCriteria;
import com.xiyu.bid.templatecatalog.domain.valueobject.DocumentType;
import com.xiyu.bid.templatecatalog.domain.valueobject.EnumParseResult;
import com.xiyu.bid.templatecatalog.domain.valueobject.IndustryType;
import com.xiyu.bid.templatecatalog.domain.valueobject.ProductType;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/knowledge/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Template", description = "创建模板")
    public ResponseEntity<ApiResponse<TemplateDTO>> createTemplate(@Valid @RequestBody TemplateMutationRequest request) {
        sanitizeTemplateMutationRequest(request);
        TemplateDTO created = templateService.createTemplate(toTemplateDTO(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Template created successfully", created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Template", description = "获取所有模板")
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> getAllTemplates(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) com.xiyu.bid.entity.Template.Category category,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String documentType) {
        EnumParseResult<ProductType> productTypeResult = ProductType.parse(productType);
        EnumParseResult<IndustryType> industryResult = IndustryType.parse(industry);
        EnumParseResult<DocumentType> documentTypeResult = DocumentType.parse(documentType);
        requireValidEnum(productTypeResult);
        requireValidEnum(industryResult);
        requireValidEnum(documentTypeResult);
        TemplateQueryCriteria criteria = TemplateQueryCriteria.builder()
                .name(name == null ? null : InputSanitizer.sanitizeString(name, 200))
                .category(category)
                .productType(productTypeResult.value())
                .industry(industryResult.value())
                .documentType(documentTypeResult.value())
                .build();
        List<TemplateDTO> templates = templateService.getAllTemplates(criteria);
        return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully", templates));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Template", description = "根据ID获取模板")
    public ResponseEntity<ApiResponse<TemplateDTO>> getTemplateById(@PathVariable Long id) {
        TemplateDTO template = templateService.getTemplateById(id);
        return ResponseEntity.ok(ApiResponse.success("Template retrieved successfully", template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "UPDATE", entityType = "Template", description = "更新模板")
    public ResponseEntity<ApiResponse<TemplateDTO>> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateMutationRequest request) {
        sanitizeTemplateMutationRequest(request);
        TemplateDTO updated = templateService.updateTemplate(id, toTemplateDTO(request));
        return ResponseEntity.ok(ApiResponse.success("Template updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "DELETE", entityType = "Template", description = "删除模板")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Template", description = "根据类别获取模板")
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> getTemplatesByCategory(@PathVariable com.xiyu.bid.entity.Template.Category category) {
        List<TemplateDTO> templates = templateService.getTemplatesByCategory(category);
        return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully", templates));
    }

    @GetMapping("/creator/{createdBy}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Template", description = "根据创建者获取模板")
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> getTemplatesByCreator(@PathVariable Long createdBy) {
        List<TemplateDTO> templates = templateService.getTemplatesByCreatedBy(createdBy);
        return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully", templates));
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Template", description = "复制模板")
    public ResponseEntity<ApiResponse<TemplateDTO>> copyTemplate(@PathVariable Long id, @Valid @RequestBody TemplateCopyRequest request) {
        request.setName(InputSanitizer.sanitizeString(request.getName(), 200));
        TemplateDTO copied = templateService.copyTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success("Template copied successfully", copied));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Template", description = "获取模板版本历史")
    public ResponseEntity<ApiResponse<List<TemplateVersionDTO>>> getTemplateVersions(@PathVariable Long id) {
        List<TemplateVersionDTO> versions = templateService.getTemplateVersions(id);
        return ResponseEntity.ok(ApiResponse.success("Template versions retrieved successfully", versions));
    }

    @PostMapping("/{id}/use-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Template", description = "记录模板使用")
    public ResponseEntity<ApiResponse<TemplateUseRecordDTO>> createTemplateUseRecord(@PathVariable Long id, @Valid @RequestBody TemplateUseRecordRequest request) {
        request.setDocumentName(InputSanitizer.sanitizeString(request.getDocumentName(), 255));
        request.setDocType(InputSanitizer.sanitizeString(request.getDocType(), 100));
        TemplateUseRecordDTO created = templateService.createTemplateUseRecord(id, request);
        return ResponseEntity.ok(ApiResponse.success("Template use recorded successfully", created));
    }

    @PostMapping("/{id}/downloads")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "CREATE", entityType = "Template", description = "记录模板下载")
    public ResponseEntity<ApiResponse<TemplateDTO>> createTemplateDownloadRecord(@PathVariable Long id, @RequestBody(required = false) TemplateDownloadRecordRequest request) {
        TemplateDTO updated = templateService.createTemplateDownloadRecord(id, request);
        return ResponseEntity.ok(ApiResponse.success("Template download recorded successfully", updated));
    }

    private void sanitizeTemplateMutationRequest(TemplateMutationRequest request) {
        if (request.getName() != null) request.setName(InputSanitizer.sanitizeString(request.getName(), 200));
        if (request.getProductType() != null) request.setProductType(InputSanitizer.sanitizeString(request.getProductType(), 100));
        if (request.getIndustry() != null) request.setIndustry(InputSanitizer.sanitizeString(request.getIndustry(), 100));
        if (request.getDocumentType() != null) request.setDocumentType(InputSanitizer.sanitizeString(request.getDocumentType(), 100));
        if (request.getFileUrl() != null) request.setFileUrl(InputSanitizer.sanitizeString(request.getFileUrl(), 500));
        if (request.getDescription() != null) request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 2000));
        if (request.getFileSize() != null) request.setFileSize(InputSanitizer.sanitizeString(request.getFileSize(), 100));
        if (request.getTags() != null) request.setTags(request.getTags().stream().map(tag -> InputSanitizer.sanitizeString(tag, 50)).toList());
    }

    private TemplateDTO toTemplateDTO(TemplateMutationRequest request) {
        EnumParseResult<ProductType> productTypeResult = ProductType.parse(request.getProductType());
        EnumParseResult<IndustryType> industryResult = IndustryType.parse(request.getIndustry());
        EnumParseResult<DocumentType> documentTypeResult = DocumentType.parse(request.getDocumentType());
        requireValidEnum(productTypeResult);
        requireValidEnum(industryResult);
        requireValidEnum(documentTypeResult);
        return TemplateDTO.builder()
                .name(request.getName())
                .category(request.getCategory())
                .productType(productTypeResult.value())
                .industry(industryResult.value())
                .documentType(documentTypeResult.value())
                .fileUrl(request.getFileUrl())
                .description(request.getDescription())
                .fileSize(request.getFileSize())
                .tags(request.getTags())
                .createdBy(request.getCreatedBy())
                .build();
    }

    private void requireValidEnum(EnumParseResult<?> parseResult) {
        if (!parseResult.valid()) {
            throw new IllegalArgumentException(parseResult.message());
        }
    }
}
