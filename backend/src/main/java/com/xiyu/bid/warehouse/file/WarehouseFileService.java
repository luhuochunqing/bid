package com.xiyu.bid.warehouse.file;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseFileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final WarehouseAttachmentRepository attachmentRepository;
    private final WarehouseRepository warehouseRepository;

    @Value("${warehouse.attachment.root:/data/attachments/warehouse}")
    private String rootPath;

    @Transactional
    public WarehouseAttachmentEntity upload(Long warehouseId,
                                          WarehouseAttachmentType type,
                                          MultipartFile file,
                                          Long uploadedBy) {
        // 1. Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 PDF/JPG/PNG");
        }

        // 2. Validate size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }

        // 3. Build storage path
        String ext = getExtension(file.getOriginalFilename(), contentType);
        String storedFilename = buildStoredFilename(warehouseId, type, ext);
        Path dir = Paths.get(rootPath, String.valueOf(warehouseId));
        Path targetPath = dir.resolve(storedFilename);

        // 4. Write file to disk
        try {
            Files.createDirectories(dir);
            file.transferTo(targetPath.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("文件写入失败: " + ex.getMessage(), ex);
        }

        // 5. Persist entity
        WarehouseEntity warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("仓库不存在: " + warehouseId));
        WarehouseAttachmentEntity entity = WarehouseAttachmentEntity.builder()
                .warehouse(warehouse)
                .type(type)
                .originalFilename(sanitizeDisplayName(file.getOriginalFilename()))
                .storedFilename(storedFilename)
                .fileSize(file.getSize())
                .contentType(contentType)
                .uploadedBy(uploadedBy)
                .uploadedAt(LocalDateTime.now())
                .build();
        return attachmentRepository.save(entity);
    }

    @Transactional
    public void delete(WarehouseAttachmentEntity attachment) {
        // 1. Delete file from disk
        Path path = Paths.get(rootPath,
                String.valueOf(attachment.getWarehouse().getId()),
                attachment.getStoredFilename());
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Failed to delete file: {}", attachment.getStoredFilename(), ex);
        }
        // 2. Delete entity
        attachmentRepository.delete(attachment);
    }

    public Resource download(WarehouseAttachmentEntity attachment) {
        Path path = Paths.get(rootPath,
                String.valueOf(attachment.getWarehouse().getId()),
                attachment.getStoredFilename());
        return new FileSystemResource(path);
    }

    public String sanitizeDisplayName(String filename) {
        if (filename == null) return "unnamed";
        int lastSep = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return lastSep >= 0 ? filename.substring(lastSep + 1) : filename;
    }

    private String buildStoredFilename(Long warehouseId, WarehouseAttachmentType type, String ext) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return String.format("WH_%d_%s_%s.%s", warehouseId, type.name(), ts, ext);
    }

    private String getExtension(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                return filename.substring(dot + 1).toLowerCase();
            }
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "pdf";
        };
    }
}
