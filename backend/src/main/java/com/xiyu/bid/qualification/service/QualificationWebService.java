// Input: qualification multipart files and upload parameters
// Output: import results and updated qualification attachment DTOs
// Pos: Service/Web服务适配层
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationAttachmentJpaRepository;
import com.xiyu.bid.businessqualification.application.service.ImportQualificationAppService;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.application.QualificationQueryService;
import com.xiyu.bid.qualification.dto.QualificationAttachmentDTO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QualificationWebService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png",
            "doc", "docx", "xls", "xlsx"
    );
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ImportQualificationAppService importQualificationAppService;
    private final QualificationService qualificationService;
    private final QualificationQueryService qualificationQueryService;
    private final QualificationAttachmentJpaRepository qualificationAttachmentJpaRepository;

    @Value("${qualification.attachment.storage-path:data/qualification-attachments}")
    private String storageRoot;

    public record AttachmentFile(Path path, String fileName, String contentType) {}

    /** 保存文件到磁盘的结果 */
    private record SavedFile(String originalFilename, String uniqueFilename, Path storagePath) {}

    public ImportQualificationAppService.ImportSummary importFromExcel(MultipartFile file, String operatorName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        return importQualificationAppService.importFromExcel(file, operatorName);
    }

    public QualificationDTO uploadAttachment(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }

        validateFileType(file);
        SavedFile saved = saveFileToDisk(id, file);

        try {
            // 获取当前 DTO，追加附件到已有列表（防止覆盖已有附件）
            QualificationDTO dto = qualificationQueryService.getQualificationById(id);
            dto.setFileUrl(saved.uniqueFilename());

            QualificationAttachmentDTO attachmentDTO = QualificationAttachmentDTO.builder()
                    .fileName(saved.originalFilename())
                    .fileUrl(saved.uniqueFilename())
                    .uploadedAt(LocalDateTime.now().toString())
                    .build();

            List<QualificationAttachmentDTO> existingAttachments = dto.getAttachments();
            if (existingAttachments == null || existingAttachments.isEmpty()) {
                dto.setAttachments(List.of(attachmentDTO));
            } else {
                List<QualificationAttachmentDTO> merged = new ArrayList<>(existingAttachments);
                merged.add(attachmentDTO);
                dto.setAttachments(merged);
            }

            return qualificationService.updateQualification(id, dto);
        } catch (RuntimeException e) {
            cleanupOrphanFile(saved.storagePath());
            throw e;
        }
    }

    public QualificationDTO replaceAttachment(Long id, Long attachmentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }

        // 1. 校验附件归属该资质
        var existing = qualificationAttachmentJpaRepository.findById(attachmentId)
                .filter(a -> a.getQualificationId().equals(id))
                .orElseThrow(() -> new InvalidArgumentException("附件不存在或不属于该资质"));

        validateFileType(file);

        // 2. 缓存旧文件路径用于后续清理
        String oldFileUrl = existing.getFileUrl();

        // 3. 保存新文件
        SavedFile saved = saveFileToDisk(id, file);

        // 4. 更新 DB 记录（替换原附件的文件名和 URL，而非新增记录）
        try {
            existing.setFileName(saved.originalFilename());
            existing.setFileUrl(saved.uniqueFilename());
            existing.setUploadedAt(LocalDateTime.now());
            qualificationAttachmentJpaRepository.save(existing);

            // 5. 同步主实体 fileUrl
            syncFileUrlToMainEntity(id, saved.uniqueFilename());

            // 6. 清理旧物理文件
            cleanupOldFile(id, oldFileUrl);

            return qualificationQueryService.getQualificationById(id);
        } catch (RuntimeException e) {
            cleanupOrphanFile(saved.storagePath());
            throw e;
        }
    }

    /**
     * 获取附件文件信息，用于下载。
     * 包含路径遍历防护和文件存在性检查。
     */
    public AttachmentFile getAttachmentFile(Long id, Long attachmentId) {
        var attachmentOpt = qualificationAttachmentJpaRepository.findById(attachmentId);
        if (attachmentOpt.isEmpty() || !attachmentOpt.get().getQualificationId().equals(id)) {
            throw new InvalidArgumentException("附件不存在");
        }
        var attachment = attachmentOpt.get();
        String fileUrl = attachment.getFileUrl();
        if (fileUrl == null || InputSanitizer.detectPathTraversal(fileUrl)) {
            throw new InvalidArgumentException("非法的文件路径");
        }
        Path path = resolveAttachmentPath(id, fileUrl);
        if (!Files.exists(path)) {
            throw new InvalidArgumentException("附件文件不存在");
        }
        return new AttachmentFile(path, attachment.getFileName(), probeContentType(path));
    }

    private void validateFileType(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new InvalidArgumentException("不支持的文件类型: " + ext);
            }
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new InvalidArgumentException("不支持的文件 MIME 类型: " + contentType);
        }
    }

    /** 保存上传文件到磁盘，返回文件名信息和存储路径 */
    private SavedFile saveFileToDisk(Long qualificationId, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String safeFilename = InputSanitizer.sanitizeFilename(originalFilename != null ? originalFilename : "attachment");
        String uniqueFilename = System.currentTimeMillis() + "_" + safeFilename;
        Path storagePath = resolveAttachmentPath(qualificationId, uniqueFilename);

        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("附件保存失败: " + e.getMessage(), e);
        }

        return new SavedFile(originalFilename, uniqueFilename, storagePath);
    }

    /** 同步最新附件 URL 到主实体 fileUrl 字段 */
    private void syncFileUrlToMainEntity(Long qualificationId, String uniqueFilename) {
        QualificationDTO dto = qualificationQueryService.getQualificationById(qualificationId);
        dto.setFileUrl(uniqueFilename);
        qualificationService.updateQualification(qualificationId, dto);
    }

    /** 清理旧物理文件 */
    private void cleanupOldFile(Long qualificationId, String oldFileUrl) {
        if (oldFileUrl != null) {
            try {
                Path oldPath = resolveAttachmentPath(qualificationId, oldFileUrl);
                Files.deleteIfExists(oldPath);
            } catch (IOException e) {
                log.warn("清理旧附件文件失败: {}", oldFileUrl, e);
            }
        }
    }

    /** 清理因 DB 写入失败产生的孤立文件 */
    private void cleanupOrphanFile(Path storagePath) {
        try {
            Files.deleteIfExists(storagePath);
        } catch (IOException e) {
            log.warn("清理孤立附件文件失败: {}", storagePath, e);
        }
    }

    private Path resolveAttachmentPath(Long id, String filename) {
        Path root = getStorageRoot();
        Path resolved = root.resolve(id.toString()).resolve(filename).normalize();
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
