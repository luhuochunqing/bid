package com.xiyu.bid.file.adapter.web;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.file.application.CompleteUploadUseCase;
import com.xiyu.bid.file.application.GetDownloadUrlUseCase;
import com.xiyu.bid.file.application.IssueUploadTokenUseCase;
import com.xiyu.bid.file.dto.DownloadUrlResponse;
import com.xiyu.bid.file.dto.UploadCompletedRequest;
import com.xiyu.bid.file.dto.UploadTokenRequest;
import com.xiyu.bid.file.dto.UploadTokenResponse;
import com.xiyu.bid.security.CurrentUserResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大文件上传控制器。
 *
 * <p>浏览器通过 /upload-token 获取华为云 OBS 临时凭证，直传 OBS 完成后回调 /{uploadId}/completed。</p>
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final IssueUploadTokenUseCase issueUploadTokenUseCase;
    private final CompleteUploadUseCase completeUploadUseCase;
    private final GetDownloadUrlUseCase getDownloadUrlUseCase;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/upload-token")
    public ApiResponse<UploadTokenResponse> issueUploadToken(@RequestBody @Valid UploadTokenRequest request) {
        Long userId = requireUserId();
        return ApiResponse.success(issueUploadTokenUseCase.execute(request, userId));
    }

    @PostMapping("/{uploadId}/completed")
    public ApiResponse<Void> completeUpload(
            @PathVariable String uploadId,
            @RequestBody @Valid UploadCompletedRequest request) {
        Long userId = requireUserId();
        completeUploadUseCase.execute(uploadId, request, userId);
        return ApiResponse.success("上传完成通知已接收", null);
    }

    @GetMapping("/{uploadId}/download-url")
    public ApiResponse<DownloadUrlResponse> getDownloadUrl(
            @PathVariable String uploadId,
            @RequestParam(defaultValue = "300") int expireSeconds) {
        Long userId = requireUserId();
        return ApiResponse.success(getDownloadUrlUseCase.execute(uploadId, expireSeconds, userId));
    }

    private Long requireUserId() {
        Long userId = currentUserResolver.getCurrentUserId();
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException("用户未认证");
        }
        return userId;
    }
}
