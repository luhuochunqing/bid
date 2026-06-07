// Input: resources service and request DTOs
// Output: Bar Certificate REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.dto.BarCertificateBorrowRecordDTO;
import com.xiyu.bid.resources.dto.BarCertificateBorrowRequest;
import com.xiyu.bid.resources.dto.BarCertificateCreateRequest;
import com.xiyu.bid.resources.dto.BarCertificateResponseDTO;
import com.xiyu.bid.resources.dto.BarCertificateReturnRequest;
import com.xiyu.bid.resources.dto.BarCertificateUpdateRequest;
import com.xiyu.bid.resources.service.BarCertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resources/bar-assets/{assetId}/certificates")
@RequiredArgsConstructor
public class BarCertificateController {

    private final BarCertificateService barCertificateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BarCertificateResponseDTO>>> getCertificates(@PathVariable Long assetId) {
        return ResponseEntity.ok(ApiResponse.success(barCertificateService.getCertificates(assetId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "BarCertificate", description = "Create BAR certificate")
    public ResponseEntity<ApiResponse<BarCertificateResponseDTO>> createCertificate(
            @PathVariable Long assetId,
            @Valid @RequestBody BarCertificateCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certificate created successfully",
                barCertificateService.createCertificate(assetId, request)));
    }

    @PutMapping("/{certificateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "BarCertificate", description = "Update BAR certificate")
    public ResponseEntity<ApiResponse<BarCertificateResponseDTO>> updateCertificate(
            @PathVariable Long assetId,
            @PathVariable Long certificateId,
            @Valid @RequestBody BarCertificateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certificate updated successfully",
                barCertificateService.updateCertificate(assetId, certificateId, request)));
    }

    @DeleteMapping("/{certificateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "BarCertificate", description = "Delete BAR certificate")
    public ResponseEntity<ApiResponse<Void>> deleteCertificate(
            @PathVariable Long assetId,
            @PathVariable Long certificateId) {
        barCertificateService.deleteCertificate(assetId, certificateId);
        return ResponseEntity.ok(ApiResponse.success("Certificate deleted successfully", null));
    }

    @PostMapping("/{certificateId}/borrow")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "BORROW", entityType = "BarCertificate", description = "Borrow BAR certificate")
    public ResponseEntity<ApiResponse<BarCertificateResponseDTO>> borrowCertificate(
            @PathVariable Long assetId,
            @PathVariable Long certificateId,
            @Valid @RequestBody BarCertificateBorrowRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certificate borrowed successfully",
                barCertificateService.borrowCertificate(assetId, certificateId, request)));
    }

    @PostMapping("/{certificateId}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "RETURN", entityType = "BarCertificate", description = "Return BAR certificate")
    public ResponseEntity<ApiResponse<BarCertificateResponseDTO>> returnCertificate(
            @PathVariable Long assetId,
            @PathVariable Long certificateId,
            @RequestBody(required = false) BarCertificateReturnRequest request) {
        BarCertificateReturnRequest safeRequest = request != null ? request : new BarCertificateReturnRequest();
        return ResponseEntity.ok(ApiResponse.success("Certificate returned successfully",
                barCertificateService.returnCertificate(assetId, certificateId, safeRequest)));
    }

    @GetMapping("/{certificateId}/borrow-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<BarCertificateBorrowRecordDTO>>> getBorrowRecords(
            @PathVariable Long assetId,
            @PathVariable Long certificateId) {
        return ResponseEntity.ok(ApiResponse.success(
                barCertificateService.getBorrowRecords(assetId, certificateId)));
    }
}
