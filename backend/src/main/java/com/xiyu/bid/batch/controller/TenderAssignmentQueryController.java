package com.xiyu.bid.batch.controller;

import com.xiyu.bid.batch.dto.TenderAssignmentCandidateResponse;
import com.xiyu.bid.batch.dto.TenderAssignmentResponse;
import com.xiyu.bid.batch.service.BatchTenderAssignmentService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenders")
@RequiredArgsConstructor
public class TenderAssignmentQueryController {

    private final BatchTenderAssignmentService batchTenderAssignmentService;

    @GetMapping("/{id}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<TenderAssignmentResponse>> getTenderAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(batchTenderAssignmentService.getAssignment(id)));
    }

    @GetMapping("/assignment-candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<TenderAssignmentCandidateResponse>>> getTenderAssignmentCandidates() {
        return ResponseEntity.ok(ApiResponse.success(batchTenderAssignmentService.getCandidates()));
    }
}
