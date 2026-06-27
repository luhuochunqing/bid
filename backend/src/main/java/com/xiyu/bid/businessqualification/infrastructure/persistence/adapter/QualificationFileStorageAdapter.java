package com.xiyu.bid.businessqualification.infrastructure.persistence.adapter;

import com.xiyu.bid.businessqualification.domain.port.QualificationFileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 资质证书文件存储适配器。
 * 将文件存储到本地文件系统，并返回可下载的 API URL。
 * 文件格式：QUAL_{certificateNo}_{sequence}_{qualificationName}.{ext}
 */
@Component
public class QualificationFileStorageAdapter implements QualificationFileStorage {

    private static final String BASE_DIR = "data/qualification-attachments";

    @Override
    public String storeAttachment(Long qualificationId, byte[] content, String originalFilename, String contentType) {
        String fileName = generateStandardFileName(null, 1, null, originalFilename);
        return storeToFile(qualificationId, fileName, content);
    }

    @Override
    public String storeAttachmentWithNaming(
            Long qualificationId,
            byte[] content,
            String certificateNo,
            int sequence,
            String qualificationName,
            String originalFilename,
            String contentType
    ) {
        String safeFileName = generateStandardFileName(certificateNo, sequence, qualificationName, originalFilename);
        return storeToFile(qualificationId, safeFileName, content);
    }

    @Override
    public String generateStandardFileName(String certificateNo, int sequence, String qualificationName, String originalFilename) {
        String certNo = certificateNo != null ? sanitizeFileNameComponent(certificateNo) : "CERT";
        String name = qualificationName != null ? sanitizeFileNameComponent(qualificationName) : "资质证书";
        String ext = extractExtension(originalFilename);

        return String.format("QUAL_%s_%02d_%s.%s", certNo, sequence, name, ext);
    }

    private String sanitizeFileNameComponent(String input) {
        if (input == null || input.isBlank()) {
            return "未知";
        }
        return input.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "pdf";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "pdf";
    }

    private String storeToFile(Long qualificationId, String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("附件文件为空");
        }
        if (fileName == null || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("文件名含非法字符: " + fileName);
        }
        if (qualificationId == null) {
            throw new IllegalArgumentException("资质ID不能为空");
        }
        try {
            Path baseDir = Paths.get(BASE_DIR).toAbsolutePath().normalize();
            Path dir = baseDir.resolve(String.valueOf(qualificationId));
            Files.createDirectories(dir);

            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(baseDir)) {
                throw new IllegalArgumentException("路径遍历拒绝: " + fileName);
            }

            Files.write(target, content,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

            return fileName;

        } catch (IOException e) {
            throw new RuntimeException("存储资质证书附件失败: " + e.getMessage(), e);
        }
    }
}
