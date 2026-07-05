package com.xiyu.bid.brandauth.manufacturer.infrastructure;

import com.xiyu.bid.brandauth.manufacturer.application.service.BrandAuthZipExporter;
import com.xiyu.bid.entity.RoleProfileCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** REST controller for brand authorization ZIP export (Excel + attachments). */
@RestController
@RequestMapping("/api/knowledge/brand-auth")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class BrandAuthZipExportController {

    private static final String VIEW_PERM =
            RoleProfileCatalog.BRAND_AUTH_VIEW_PERMISSION;

    private final BrandAuthZipExporter zipExporter;

    /** Export authorizations as ZIP (Excel + attachments). */
    @GetMapping("/export-zip")
    @PreAuthorize("hasAuthority('" + VIEW_PERM + "')")
    public ResponseEntity<byte[]> exportZip(
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
            @RequestParam(required = false) final List<String> attachmentTypes)
            throws IOException {
        var filter = BrandAuthFilterMapper.buildFilter(
                productLines, brandId, brandName,
                importDomestic, manufacturerName,
                authStartFrom, authStartTo, authEndFrom, authEndTo,
                statuses, keyword, authorizationType);
        byte[] data = zipExporter.exportZip(filter, attachmentTypes);
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("yyyyMMdd_HHmmss"));
        String authLabel = "AGENT".equals(authorizationType)
                ? "代理商授权" : "原厂授权";
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + authLabel + "导出_"
                        + timestamp + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(data);
    }
}
