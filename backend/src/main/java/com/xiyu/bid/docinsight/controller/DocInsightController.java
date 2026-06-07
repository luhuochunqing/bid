// Input: HTTP multipart/form-data 上传请求（profile, entityId, file）
// Output: DocumentAnalysisResult 包装在 ApiResponse；边界校验（大小、类型、参数格式、项目访问范围）
// Pos: docinsight/controller — 文档智能分析 REST 入口
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.application.DocumentIntelligenceService;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/doc-insight")
@RequiredArgsConstructor
public class DocInsightController {

    /** 上传文件大小上限（MB），通过 app.docinsight.max-upload-mb 配置，默认 50 MB。 */
    @Value("${app.docinsight.max-upload-mb:50}")
    private int maxUploadMb;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private static final String TEXT_PLAIN = "text/plain";

    /** profileCode: 字母、数字、下划线、短横线，1-64 字符。 */
    private static final Pattern PROFILE_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");

    /** entityId: 字母、数字、下划线、短横线，1-128 字符。 */
    private static final Pattern ENTITY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]{1,128}");

    private final DocumentIntelligenceService docInsightService;

    @PostMapping("/parse")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<DocumentAnalysisResult>> parse(
            @RequestParam("profile") String profileCode,
            @RequestParam("entityId") String entityId,
            @RequestParam("file") MultipartFile file) {

        // ── 0. Empty file guard ───────────────────────────────────────────────
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "上传文件为空或未正确传输"));
        }

        // ── 1. File size guard ────────────────────────────────────────────────
        long maxBytes = (long) maxUploadMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error(413, "上传文件过大"));
        }

        // ── 2. profileCode validation ─────────────────────────────────────────
        if (profileCode == null || profileCode.isBlank()
                || !PROFILE_CODE_PATTERN.matcher(profileCode).matches()) {
            throw new IllegalArgumentException("无效的解析配置标识");
        }

        // ── 3. Content-type allowlist ─────────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedContentType(profileCode, contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(ApiResponse.error(415, "不支持的文件类型"));
        }

        // ── 4. entityId validation ────────────────────────────────────────────
        if (entityId == null || entityId.isBlank()
                || !ENTITY_ID_PATTERN.matcher(entityId).matches()) {
            throw new IllegalArgumentException("无效的实体标识");
        }

        // ── 5. Access scope + analysis (service layer guards project access) ──
        DocumentAnalysisResult result = docInsightService.process(profileCode, entityId, file);
        return ResponseEntity.ok(ApiResponse.success("文档解析完成", result));
    }

    private boolean isAllowedContentType(String profileCode, String contentType) {
        String normalizedContentType = contentType.toLowerCase();
        return ALLOWED_CONTENT_TYPES.contains(normalizedContentType)
                || (TEXT_PLAIN.equals(normalizedContentType) && DocInsightProfiles.isTenderIntake(profileCode));
    }
}
