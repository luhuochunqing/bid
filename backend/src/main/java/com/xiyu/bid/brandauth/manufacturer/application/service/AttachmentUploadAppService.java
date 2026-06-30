package com.xiyu.bid.brandauth.manufacturer.application.service;

import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.port.ManufacturerAuthorizationRepository;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AttachmentType;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.BrandAuthAttachmentEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.BrandAuthAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentUploadAppService {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/jpg");

    @Value("${app.upload.brand-auth-dir:uploads/brand-auth}")
    private String uploadDir;

    private final BrandAuthAttachmentJpaRepository attachmentRepository;
    private final ManufacturerAuthorizationRepository authorizationRepository;

    @Transactional
    public List<ManufacturerAuthorizationDTO.AttachmentDTO> upload(
            Long authorizationId, String attachmentType, List<MultipartFile> files) throws IOException {
        ManufacturerAuthorization auth = authorizationRepository.findById(authorizationId)
                .orElseThrow(() -> new NoSuchElementException("授权记录不存在: " + authorizationId));

        // 关键修复：必须把 uploadDir 归一化为绝对路径，否则会触发 Spring 的相对路径陷阱
        // - Files.createDirectories(Path) 相对路径按 JVM 工作目录解析（systemd WorkingDirectory）
        // - MultipartFile.transferTo(File) 相对路径按 Tomcat 临时工作目录解析
        // 两者基准不一致会导致 FileNotFoundException
        Path uploadPath = resolveAbsoluteUploadPath().resolve(String.valueOf(authorizationId));
        Files.createDirectories(uploadPath);

        List<BrandAuthAttachmentEntity> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);

            String storedName = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
            Path dest = uploadPath.resolve(storedName).toAbsolutePath().normalize();
            // 使用 Path 重载 + 绝对路径，绕开 Tomcat 临时目录陷阱
            file.transferTo(dest);

            BrandAuthAttachmentEntity entity = BrandAuthAttachmentEntity.builder()
                    .authorizationId(authorizationId)
                    .attachmentType(AttachmentType.valueOf(attachmentType))
                    .fileName(file.getOriginalFilename())
                    .fileUrl(dest.toString())
                    .fileSize(file.getSize())
                    .fileType(file.getContentType())
                    .build();
            saved.add(attachmentRepository.save(entity));
        }
        return saved.stream()
                .map(e -> new ManufacturerAuthorizationDTO.AttachmentDTO(
                        e.getId(), e.getAttachmentType().name(), e.getFileName(),
                        e.getFileUrl(), e.getFileSize(), e.getFileType(), e.getCreatedAt()))
                .collect(Collectors.toList());
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("文件为空: " + file.getOriginalFilename());
        if (file.getSize() > MAX_FILE_SIZE)
            throw new IllegalArgumentException("文件超过20MB: " + file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new IllegalArgumentException("不支持的文件格式: " + file.getContentType() + " (仅支持 PDF/JPG/PNG)");
    }

    private static String sanitizeFilename(String name) {
        return name == null ? "file" : name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
    }

    /**
     * 将 uploadDir 归一化为绝对路径。
     * 相对路径按 JVM 工作目录（systemd WorkingDirectory）解析，确保
     * Files.createDirectories 与 MultipartFile.transferTo 使用同一基准。
     */
    private Path resolveAbsoluteUploadPath() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
        }
        return p;
    }

    public List<BrandAuthAttachmentEntity> getAttachments(Long authorizationId) {
        return attachmentRepository.findByAuthorizationId(authorizationId);
    }
}
