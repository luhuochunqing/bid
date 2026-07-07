// Input: qualification audit log multipart files
// Output: updated qualification DTO with audit_log_file_url field
// Pos: Service/Web服务适配层
// 维护声明: CO-530 审核日志附件上传/下载独立服务，从 QualificationWebService 拆分以遵守 line-budget。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.application.QualificationQueryService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QualificationAuditLogWebService {

    /** CO-530: 审核日志附件限定 Word/PDF/PNG */
    private static final Set<String> ALLOWED_AUDIT_LOG_EXTENSIONS = Set.of("pdf", "png", "doc", "docx");
    private static final Set<String> ALLOWED_AUDIT_LOG_MIME_TYPES = Set.of(
            "application/pdf", "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final QualificationService qualificationService;
    private final QualificationQueryService qualificationQueryService;

    @Value("${qualification.attachment.storage-path:data/qualification-attachments}")
    private String storageRoot;

    /** 保存文件到磁盘的结果 */
    private record SavedFile(String originalFilename, String uniqueFilename, Path storagePath) {}

    /** 获取附件文件信息的结果 */
    public record AttachmentFile(Path path, String fileName, String contentType) {}

    /**
     * CO-530: 上传审核日志附件（Word/PDF/PNG，非必填）。
     * 文件存储在 {storageRoot}/{id}/audit-log/{uniqueFilename} 子目录，与主附件隔离。
     * 替换时清理旧文件。更新 audit_log_file_url 字段。
     */
    public QualificationDTO uploadAuditLogAttachment(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        validateAuditLogFileType(file);

        QualificationDTO dto = qualificationQueryService.getQualificationById(id);
        String oldAuditLogFileUrl = dto.getAuditLogFileUrl();

        SavedFile saved = saveAuditLogToDisk(id, file);
        try {
            dto.setAuditLogFileUrl(saved.uniqueFilename());
            QualificationDTO updated = qualificationService.updateQualification(id, dto);
            cleanupOldAuditLogFile(id, oldAuditLogFileUrl);
            return updated;
        } catch (RuntimeException e) {
            cleanupOrphanFile(saved.storagePath());
            throw e;
        }
    }

    /**
     * CO-530: 获取审核日志附件文件信息，用于下载。
     * 文件名从 auditLogFileUrl 中提取（uniqueFilename 格式：{timestamp}_{originalFilename}）。
     */
    public AttachmentFile getAuditLogFile(Long id) {
        QualificationDTO dto = qualificationQueryService.getQualificationById(id);
        String fileUrl = dto.getAuditLogFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new InvalidArgumentException("该资质未上传审核日志附件");
        }
        if (InputSanitizer.detectPathTraversal(fileUrl)) {
            throw new InvalidArgumentException("非法的文件路径");
        }
        Path path = resolveAuditLogPath(id, fileUrl);
        if (!Files.exists(path)) {
            throw new InvalidArgumentException("审核日志附件不存在");
        }
        String displayName = extractOriginalFilename(fileUrl);
        return new AttachmentFile(path, displayName, probeContentType(path));
    }

    /** 从 uniqueFilename（{timestamp}_{originalFilename}）中提取原始文件名作为下载显示名 */
    private String extractOriginalFilename(String uniqueFilename) {
        int underscoreIdx = uniqueFilename.indexOf('_');
        return underscoreIdx >= 0 && underscoreIdx < uniqueFilename.length() - 1
                ? uniqueFilename.substring(underscoreIdx + 1)
                : uniqueFilename;
    }

    private void validateAuditLogFileType(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new InvalidArgumentException("审核日志附件必须包含文件扩展名");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_AUDIT_LOG_EXTENSIONS.contains(ext)) {
            throw new InvalidArgumentException("审核日志附件仅支持 Word/PDF/PNG，当前类型: " + ext);
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_AUDIT_LOG_MIME_TYPES.contains(contentType)) {
            throw new InvalidArgumentException("审核日志附件 MIME 类型不支持: " + contentType);
        }
    }

    /** 保存审核日志附件到磁盘 {storageRoot}/{id}/audit-log/ 子目录 */
    private SavedFile saveAuditLogToDisk(Long qualificationId, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String safeFilename = InputSanitizer.sanitizeFilename(originalFilename != null ? originalFilename : "audit-log");
        String uniqueFilename = System.currentTimeMillis() + "_" + safeFilename;
        Path storagePath = resolveAuditLogPath(qualificationId, uniqueFilename);

        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("审核日志附件保存失败: " + e.getMessage(), e);
        }
        return new SavedFile(originalFilename, uniqueFilename, storagePath);
    }

    /** 清理旧审核日志附件物理文件 */
    private void cleanupOldAuditLogFile(Long qualificationId, String oldAuditLogFileUrl) {
        if (oldAuditLogFileUrl != null && !oldAuditLogFileUrl.isBlank()) {
            try {
                Path oldPath = resolveAuditLogPath(qualificationId, oldAuditLogFileUrl);
                Files.deleteIfExists(oldPath);
            } catch (IOException e) {
                log.warn("清理旧审核日志附件失败: {}", oldAuditLogFileUrl, e);
            }
        }
    }

    /** 清理因 DB 写入失败产生的孤立文件 */
    private void cleanupOrphanFile(Path storagePath) {
        try {
            Files.deleteIfExists(storagePath);
        } catch (IOException e) {
            log.warn("清理孤立审核日志附件失败: {}", storagePath, e);
        }
    }

    private Path resolveAuditLogPath(Long id, String filename) {
        Path root = getStorageRoot();
        Path resolved = root.resolve(id.toString()).resolve("audit-log").resolve(filename).normalize();
        if (!resolved.startsWith(root)) {
            throw new InvalidArgumentException("非法的文件路径");
        }
        return resolved;
    }

    private Path getStorageRoot() {
        return Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    private String probeContentType(Path path) {
        try {
            String detected = Files.probeContentType(path);
            return detected != null ? detected : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
