package com.xiyu.bid.personnel.infrastructure.persistence.adapter;

import com.xiyu.bid.personnel.domain.port.PersonnelFileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class PersonnelFileStorageAdapter implements PersonnelFileStorage {

    private static final String BASE_DIR = "data/personnel-attachments";

    @Override
    public String storeCertAttachment(Long personnelId, Long certId, byte[] content, String originalFilename, String contentType) {
        String fileName = generateStandardFileName(null, null, 1, null, originalFilename);
        return storeToFile(personnelId, fileName, content);
    }

    @Override
    public String storeCertAttachmentWithNaming(
            Long personnelId,
            Long certId,
            byte[] content,
            String personnelName,
            String employeeNumber,
            int certificateSequence,
            String certificateName,
            String originalFilename,
            String contentType) {

        String safeFileName = generateStandardFileName(
                personnelName, employeeNumber, certificateSequence, certificateName, originalFilename);

        return storeToFile(personnelId, safeFileName, content);
    }

    @Override
    public String generateStandardFileName(
            String personnelName,
            String employeeNumber,
            int certificateSequence,
            String certificateName,
            String originalFilename) {

        String name = personnelName != null ? sanitizeFileNameComponent(personnelName) : "未知";
        String empNo = employeeNumber != null ? sanitizeFileNameComponent(employeeNumber) : "EMP";
        String certName = certificateName != null
                ? sanitizeFileNameComponent(certificateName)
                : "未知证书";
        String ext = extractExtension(originalFilename);

        return String.format("PER_%s_%s_%02d_%s.%s",
                name, empNo, certificateSequence, certName, ext);
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

    private String storeToFile(Long personnelId, String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("附件文件为空");
        }
        try {
            Path dir = Paths.get(BASE_DIR, String.valueOf(personnelId));
            Files.createDirectories(dir);

            Path target = dir.resolve(fileName);
            Files.write(target, content,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

            return "/api/knowledge/personnel/attachments/" + personnelId + "/" + fileName;

        } catch (IOException e) {
            throw new RuntimeException("存储人员证书附件失败: " + e.getMessage(), e);
        }
    }
}
