// Input: qualification HTTP requests, query filters, and compatibility DTOs
// Output: stable /api/knowledge/qualifications API backed by the real businessqualification domain
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议兼容与参数校验；业务规则下沉到 service.
package com.xiyu.bid.qualification.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.dto.QualificationOverviewDTO;
import com.xiyu.bid.qualification.service.BatchAttachmentService;
import com.xiyu.bid.qualification.service.QualificationService;
import com.xiyu.bid.qualification.service.QualificationAiParserService;
import com.xiyu.bid.util.InputSanitizer;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge/qualifications")
@RequiredArgsConstructor
public class QualificationController {

    private final QualificationService qualificationService;
    private final QualificationAiParserService qualificationAiParserService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "CREATE", entityType = "Qualification", description = "创建资质")
    public ResponseEntity<ApiResponse<QualificationDTO>> createQualification(@Valid @RequestBody QualificationDTO dto) {
        sanitizeQualificationDTO(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Qualification created successfully", qualificationService.createQualification(dto)));
    }

    @PostMapping("/upload-parse")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "CREATE", entityType = "Qualification", description = "AI解析资质证书")
    public ResponseEntity<ApiResponse<QualificationDTO>> uploadAndParse(@RequestParam("file") MultipartFile file) {
        QualificationDTO dto = qualificationAiParserService.extractFromPdf(file);
        return ResponseEntity.ok(ApiResponse.success("Parsed successfully", dto));
    }

    @PostMapping("/{id}/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "UPDATE", entityType = "Qualification", description = "上传资质附件")
    public ResponseEntity<ApiResponse<QualificationDTO>> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("附件上传成功",
                qualificationService.uploadAttachment(id, file)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "READ", entityType = "Qualification", description = "获取资质列表")
    public ResponseEntity<ApiResponse<Page<QualificationDTO>>> getAllQualifications(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiringFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiringTo,
            @RequestParam(required = false) String issuer,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            // CO-155 fix: pagination params. Frontend default page=0, size=15
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "15") int size
    ) {
        String sanitizedIssuer = issuer == null ? null : InputSanitizer.sanitizeString(issuer, 200);
        // CO-155 fix: size clamp, cap max 200 to prevent full-table scan
        int safeSize = size <= 0 ? 15 : Math.min(size, 200);
        int safePage = Math.max(0, page);
        return ResponseEntity.ok(ApiResponse.success("Qualifications retrieved successfully",
                qualificationService.getAllQualifications(
                        subjectType, subjectName, category, level, status,
                        expiringWithinDays, expiringFrom, expiringTo, sanitizedIssuer, keyword,
                        safePage, safeSize)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "READ", entityType = "Qualification", description = "获取资质详情")
    public ResponseEntity<ApiResponse<QualificationDTO>> getQualificationById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Qualification retrieved successfully",
                qualificationService.getQualificationById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "UPDATE", entityType = "Qualification", description = "更新资质")
    public ResponseEntity<ApiResponse<QualificationDTO>> updateQualification(@PathVariable Long id, @Valid @RequestBody QualificationDTO dto) {
        sanitizeQualificationDTO(dto);
        return ResponseEntity.ok(ApiResponse.success("Qualification updated successfully",
                qualificationService.updateQualification(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "DELETE", entityType = "Qualification", description = "删除资质")
    public ResponseEntity<Void> deleteQualification(@PathVariable Long id) {
        qualificationService.deleteQualification(id);
        return ResponseEntity.noContent().build();
    }






    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "READ", entityType = "Qualification", description = "资质概览")
    public ResponseEntity<ApiResponse<QualificationOverviewDTO>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success("Qualification overview retrieved successfully",
                qualificationService.getOverview()));
    }

    @PostMapping("/scan-expiring")
    @PreAuthorize("hasAnyRole('ADMIN', 'BID_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> scanExpiringQualifications(@RequestParam(defaultValue = "90") Integer thresholdDays) {
        return ResponseEntity.ok(ApiResponse.success("Qualification scan completed",
                qualificationService.scanExpiringQualifications(thresholdDays)));
    }

    @GetMapping("/type/{type}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    public ResponseEntity<ApiResponse<List<QualificationDTO>>> getQualificationsByType(@PathVariable com.xiyu.bid.entity.Qualification.Type type) {
        return ResponseEntity.ok(ApiResponse.success("Qualifications retrieved successfully",
                qualificationService.getQualificationsByType(type)));
    }

    @GetMapping("/valid")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    public ResponseEntity<ApiResponse<List<QualificationDTO>>> getValidQualifications() {
        return ResponseEntity.ok(ApiResponse.success("Valid qualifications retrieved successfully",
                qualificationService.getValidQualifications()));
    }

    private void sanitizeQualificationDTO(QualificationDTO dto) {
        if (dto.getName() != null) dto.setName(InputSanitizer.sanitizeString(dto.getName(), 200));
        if (dto.getSubjectName() != null) dto.setSubjectName(InputSanitizer.sanitizeString(dto.getSubjectName(), 200));
        if (dto.getCertificateNo() != null) dto.setCertificateNo(InputSanitizer.sanitizeString(dto.getCertificateNo(), 120));
        if (dto.getIssuer() != null) dto.setIssuer(InputSanitizer.sanitizeString(dto.getIssuer(), 200));
        if (dto.getAgency() != null) dto.setAgency(InputSanitizer.sanitizeString(dto.getAgency(), 200));
        if (dto.getAgencyContact() != null) dto.setAgencyContact(InputSanitizer.sanitizeString(dto.getAgencyContact(), 200));
        if (dto.getCertScope() != null) dto.setCertScope(InputSanitizer.sanitizeString(dto.getCertScope(), 1000));
        if (dto.getHolderName() != null) dto.setHolderName(InputSanitizer.sanitizeString(dto.getHolderName(), 120));
        if (dto.getRetireReason() != null) dto.setRetireReason(InputSanitizer.sanitizeString(dto.getRetireReason(), 500));
        if (dto.getFileUrl() != null) dto.setFileUrl(InputSanitizer.sanitizeString(dto.getFileUrl(), 500));
    }


    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "UPDATE", entityType = "Qualification", description = "下架资质证书")
    public ResponseEntity<ApiResponse<QualificationDTO>> retire(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "").trim();
        if (reason.length() < 4) {
            return ResponseEntity.badRequest().body(ApiResponse.error("下架原因不少于4个字"));
        }
        if (reason.length() > 200) {
            return ResponseEntity.badRequest().body(ApiResponse.error("下架原因不超过200字"));
        }
        return ResponseEntity.ok(ApiResponse.success("下架成功", qualificationService.retireQualification(id, reason)));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD')")
    @Auditable(action = "UPDATE", entityType = "Qualification", description = "恢复资质证书")
    public ResponseEntity<ApiResponse<QualificationDTO>> restore(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("恢复成功", qualificationService.restoreQualification(id)));
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_STAFF', 'BID_ADMIN', 'BID_LEAD', 'BID_SPECIALIST')")
    @Auditable(action = "READ", entityType = "Qualification", description = "获取资质等级列表")
    public ResponseEntity<ApiResponse<List<String>>> getAllLevels() {
        return ResponseEntity.ok(ApiResponse.success("Levels retrieved successfully", qualificationService.getAllLevels()));
    }
}
