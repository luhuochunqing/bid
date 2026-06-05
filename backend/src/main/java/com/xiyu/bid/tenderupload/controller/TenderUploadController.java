// Input: upload-init/upload-complete/task-status HTTP 请求
// Output: 标准化异步受理响应
// Pos: TenderUpload/Controller
// 维护声明: 控制器仅做鉴权与协议适配，业务逻辑放在 service.
package com.xiyu.bid.tenderupload.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.tenderupload.dto.TenderTaskStatusResponse;
import com.xiyu.bid.tenderupload.dto.TenderUploadCompleteRequest;
import com.xiyu.bid.tenderupload.dto.TenderUploadCompleteResponse;
import com.xiyu.bid.tenderupload.dto.TenderUploadInitRequest;
import com.xiyu.bid.tenderupload.dto.TenderUploadInitResponse;
import com.xiyu.bid.tenderupload.service.TenderUploadTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/tenders", "/v1/tenders"})
@RequiredArgsConstructor
@Slf4j
public class TenderUploadController {

    private static final String ACCESS_EXPR = "hasAnyRole('ADMIN', 'MANAGER', 'STAFF')";

    private final TenderUploadTaskService tenderUploadTaskService;

    @PostMapping("/upload-init")
    @PreAuthorize(ACCESS_EXPR)
    public ResponseEntity<ApiResponse<TenderUploadInitResponse>> initUpload(
            @Valid @RequestBody TenderUploadInitRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TenderUploadInitResponse response = tenderUploadTaskService.initUpload(request, userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Upload session created", response));
    }

    @PostMapping("/upload-complete")
    @PreAuthorize(ACCESS_EXPR)
    public ResponseEntity<ApiResponse<TenderUploadCompleteResponse>> completeUpload(
            @Valid @RequestBody TenderUploadCompleteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TenderUploadCompleteResponse response = tenderUploadTaskService.completeUpload(request, userDetails);
        return ResponseEntity.ok(ApiResponse.success("Tender accepted and queued", response));
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize(ACCESS_EXPR)
    public ResponseEntity<ApiResponse<TenderTaskStatusResponse>> getTaskStatus(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        TenderTaskStatusResponse response = tenderUploadTaskService.getTaskStatus(taskId, userDetails);
        return ResponseEntity.ok(ApiResponse.success("Task status retrieved", response));
    }
}
