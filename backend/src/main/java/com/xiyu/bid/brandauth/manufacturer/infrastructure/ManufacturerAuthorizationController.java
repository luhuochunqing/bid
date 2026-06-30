/** Infrastructure layer for manufacturer authorization module. */
package com.xiyu.bid.brandauth.manufacturer.infrastructure;

import com.xiyu.bid.brandauth.manufacturer.application.command.CreateManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.command.RevokeManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.command.UpdateManufacturerAuthCommand;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.service.AttachmentUploadAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthExportService;
import com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthImportService;
import com.xiyu.bid.brandauth.manufacturer.application.service.CreateManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.ListManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.RevokeManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.application.service.UpdateManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST controller for brand authorization CRUD and export. */
@RestController
@RequestMapping("/api/knowledge/brand-auth")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('brand-auth.view')")
public class ManufacturerAuthorizationController {

    /** Create service. */
    private final CreateManufacturerAuthAppService createService;
    /** Update service. */
    private final UpdateManufacturerAuthAppService updateService;
    /** Revoke service. */
    private final RevokeManufacturerAuthAppService revokeService;
    /** List/query service. */
    private final ListManufacturerAuthAppService listService;
    /** Attachment upload service. */
    private final AttachmentUploadAppService attachmentService;
    /** Export service. */
    private final BrandAuthExportService exportService;
    /** Import service. */
    private final BrandAuthImportService importService;
    /** User repository for auth resolution. */
    private final UserRepository userRepository;
    /** Operation log repository. */
    private final com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthOperationLogJpaRepository logRepository;

    /** List authorizations with pagination and filters. */
    @GetMapping
    @PreAuthorize("hasAuthority('brand-auth.view')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) final List<String> productLines,
            @RequestParam(required = false) final String brandId,
            @RequestParam(required = false) final String brandName,
            @RequestParam(required = false) final String importDomestic,
            @RequestParam(required = false) final String manufacturerName,
            @RequestParam(required = false) final LocalDate authStartFrom,
            @RequestParam(required = false) final LocalDate authStartTo,
            @RequestParam(required = false) final LocalDate authEndFrom,
            @RequestParam(required = false) final LocalDate authEndTo,
            @RequestParam(required = false) final List<String> statuses,
            @RequestParam(required = false) final String keyword,
            @RequestParam(required = false) final String authorizationType,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {

        List<ProductLine> productLineEnums = parseProductLines(productLines);
        List<AuthStatus> statusEnums = parseStatuses(statuses);

        var filter = new ListManufacturerAuthAppService.ListFilter(
                productLineEnums, brandId, brandName,
                importDomestic, manufacturerName,
                authStartFrom, authStartTo, authEndFrom, authEndTo,
                statusEnums, keyword, authorizationType);

        Page<ManufacturerAuthorizationDTO> result =
                listService.list(filter, page, size);
        Map<String, Object> pageData = Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize());
        return ResponseEntity.ok(ApiResponse.success(pageData));
    }

    /** Get single authorization by ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('brand-auth.view')")
    public ResponseEntity<ApiResponse<ManufacturerAuthorizationDTO>> detail(
            @PathVariable final Long id) {
        return listService.getDetail(id)
                .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new authorization. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ManufacturerAuthorizationDTO>> create(
            @Valid @RequestBody final CreateManufacturerAuthCommand cmd) {
        Long userId = getCurrentUserId();
        var result = createService.create(cmd, userId);
        // warning 作为 message 返回（方案 A）：前端 res.msg 有值则提示，无值则默认"创建成功"
        String message = result.warning() != null ? result.warning() : "创建成功";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(message, result.dto()));
    }

    /** Upload attachments for an authorization. */
    @PostMapping(value = "/attachments/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ManufacturerAuthorizationDTO.AttachmentDTO>>>
    uploadAttachments(
            @RequestParam final Long authorizationId,
            @RequestParam final String attachmentType,
            @RequestParam("files") final List<MultipartFile> files)
            throws IOException {
        List<ManufacturerAuthorizationDTO.AttachmentDTO> result =
                attachmentService.upload(
                        authorizationId, attachmentType, files);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Update an existing authorization. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ManufacturerAuthorizationDTO>> update(
            @PathVariable final Long id,
            @Valid @RequestBody final UpdateManufacturerAuthCommand cmd) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(updateService.update(id, cmd, userId)));
    }

    /** Revoke an authorization. */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ManufacturerAuthorizationDTO>> revoke(
            @PathVariable final Long id,
            @Valid @RequestBody final RevokeManufacturerAuthCommand cmd) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(revokeService.revoke(id, cmd.reason(), userId)));
    }

    /** Get operation logs for an authorization. */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> logs(
            @PathVariable final Long id) {
        List<com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthOperationLogEntity> logs =
                logRepository.findByAuthorizationIdOrderByCreatedAtDesc(id);

        List<Map<String, Object>> data = logs.stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", log.getId());
            map.put("timestamp", log.getCreatedAt().toString());
            map.put("username", log.getOperatorUsername());
            map.put("action", log.getSummary());
            map.put("description", log.getDetails() != null ? log.getDetails() : log.getRemarks());
            return map;
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** Export all authorizations as Excel. */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportAll() throws IOException {
        byte[] data = exportService.exportAll();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                    "application/"
                    + "vnd.openxmlformats-officedocument."
                    + "spreadsheetml.sheet"))
                .header("Content-Disposition",
                        "attachment; filename=品牌授权台账.xlsx")
                .body(data);
    }

    /** Download import template. */
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] data = exportService.downloadTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                    "application/"
                    + "vnd.openxmlformats-officedocument."
                    + "spreadsheetml.sheet"))
                .header("Content-Disposition",
                        "attachment; filename=品牌授权导入模板.xlsx")
                .body(data);
    }

    /** Batch import brand authorizations from an uploaded Excel file. */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BrandAuthImportService.ImportResult>> importExcel(
            @RequestParam("file") final MultipartFile file) throws IOException {
        Long userId = getCurrentUserId();
        BrandAuthImportService.ImportResult result =
                importService.importExcel(file.getBytes(), userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("未认证用户");
        }
        return userRepository.findByUsername(auth.getName())
                .map(user -> user.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "当前用户不存在: " + auth.getName()));
    }

    private static List<ProductLine> parseProductLines(
            final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<ProductLine> result = new ArrayList<>();
        for (String v : values) {
            ProductLine.fromStringOptional(v).ifPresentOrElse(
                    result::add,
                    () -> {
                        throw new IllegalArgumentException(
                                "无效的一级产线参数: " + v);
                    });
        }
        return result;
    }

    private static List<AuthStatus> parseStatuses(
            final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of(AuthStatus.ACTIVE, AuthStatus.EXPIRING_SOON,
                    AuthStatus.EXPIRED);
        }
        try {
            return values.stream().map(AuthStatus::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态参数");
        }
    }
}
