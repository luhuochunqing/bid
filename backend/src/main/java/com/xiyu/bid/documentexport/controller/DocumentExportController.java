// Input: documentexport service and request DTOs
// Output: Document Export REST API endpoints
// Pos: Controller/控制器层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.documentexport.controller;

import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentArchiveRecordDTO;
import com.xiyu.bid.documentexport.dto.DocumentCaseSnapshotDTO;
import com.xiyu.bid.documentexport.dto.DocumentExportCreateRequest;
import com.xiyu.bid.documentexport.dto.DocumentExportDTO;
import com.xiyu.bid.documentexport.service.DocumentExportService;
import com.xiyu.bid.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{projectId}")
@RequiredArgsConstructor
public class DocumentExportController {

    private final DocumentExportService documentExportService;

    @GetMapping("/exports")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<DocumentExportDTO>>> getExports(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(documentExportService.getExports(projectId)));
    }

    @PostMapping("/exports")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentExportDTO>> createExport(
            @PathVariable Long projectId,
            @Valid @RequestBody DocumentExportCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document exported successfully", documentExportService.createExport(projectId, request)));
    }

    @GetMapping("/archive-records")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<DocumentArchiveRecordDTO>>> getArchiveRecords(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(documentExportService.getArchiveRecords(projectId)));
    }

    @GetMapping("/case-snapshot")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentCaseSnapshotDTO>> getCaseSnapshot(@PathVariable Long projectId) {
        return ResponseEntity.ok(ApiResponse.success(documentExportService.getCaseSnapshot(projectId)));
    }

    @PostMapping("/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentArchiveRecordDTO>> archiveDocument(
            @PathVariable Long projectId,
            @Valid @RequestBody DocumentArchiveRecordCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document archived successfully", documentExportService.createArchiveRecord(projectId, request)));
    }
}
