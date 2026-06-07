// Input: file name, content type, byte length
// Output: ValidationResult (ok or rejection reason); allowed extensions and max size constants
// Pos: projectworkflow/core - pure file upload validation policy (PRD §5.2)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.core;

import java.util.Locale;
import java.util.Set;

/**
 * 纯核心：项目文档上传校验策略。
 * 不依赖 MultipartFile / Spring，仅基于文件名/contentType/字节长度做判定，方便测试与复用。
 *
 * PRD §5.2 约束：
 *  - 允许扩展名 / mime：png、jpg/jpeg、pdf、doc/docx、xls/xlsx
 *  - 最大文件大小 50MB
 */
public final class UploadValidationPolicy {

    public static final long MAX_BYTES = 50L * 1024L * 1024L;

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "pdf", "doc", "docx", "xls", "xlsx"
    );

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private UploadValidationPolicy() {
    }

    public static ValidationResult validate(String fileName, String contentType, long sizeBytes) {
        if (sizeBytes <= 0L) {
            return ValidationResult.reject("请上传项目文档");
        }
        if (sizeBytes > MAX_BYTES) {
            return ValidationResult.reject("项目文档大小超过 50MB 限制");
        }
        String ext = extensionOf(fileName);
        boolean extOk = ext != null && ALLOWED_EXTENSIONS.contains(ext);
        boolean ctOk = isAllowedContentType(contentType);
        if (!extOk && !ctOk) {
            return ValidationResult.reject(
                    "项目文档格式不支持，仅允许 png/jpg/jpeg/pdf/doc/docx/xls/xlsx");
        }
        return ValidationResult.ok();
    }

    private static boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return ALLOWED_CONTENT_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT));
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        String trimmed = fileName.trim();
        int dot = trimmed.lastIndexOf('.');
        if (dot < 0 || dot == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String message) {
            return new ValidationResult(false, message);
        }
    }
}
