package com.xiyu.bid.personnel.infrastructure.persistence.adapter;

import com.xiyu.bid.personnel.domain.port.PersonnelFileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 人员证书附件存储适配器（简单本地文件实现）。
 * 对应蓝图 4.3 "新增证书" Tab3 "证书附件" 必填 + ≤10MB + PDF/JPG/PNG。
 * 生产环境可替换为 OSS/MinIO 等实现同一端口。
 */
@Component
public class PersonnelFileStorageAdapter implements PersonnelFileStorage {

    private static final String BASE_DIR = "data/personnel-attachments";

    @Override
    public String storeCertAttachment(Long personnelId, Long certId, byte[] content, String originalFilename, String contentType) {
        try {
            if (content == null || content.length == 0) {
                throw new IllegalArgumentException("附件文件为空");
            }
            Path dir = Paths.get(BASE_DIR, String.valueOf(personnelId));
            Files.createDirectories(dir);

            String original = (originalFilename != null && !originalFilename.isBlank()) ? originalFilename : "certificate.pdf";
            // 安全化文件名
            String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" +
                    original.replaceAll("[^a-zA-Z0-9._-]", "_");

            Path target = dir.resolve(safeName);
            Files.write(target, content, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

            // 返回可由 controller 提供的下载端点（相对路径）
            return "/api/knowledge/personnel/attachments/" + personnelId + "/" + safeName;
        } catch (IOException e) {
            throw new RuntimeException("存储人员证书附件失败: " + e.getMessage(), e);
        }
    }
}
