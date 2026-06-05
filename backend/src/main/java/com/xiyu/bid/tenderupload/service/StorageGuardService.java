// Input: 原始文件名、相对路径和共享存储根目录
// Output: 安全文件路径、文件基础元数据和哈希计算能力
// Pos: TenderUpload/Service
// 维护声明: 仅负责存储安全与文件 I/O，不承载任务编排逻辑.
package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class StorageGuardService {

    private final TenderProcessingProperties properties;

    public String sanitizeFileName(String fileName) {
        String trimmed = InputSanitizer.sanitizeString(fileName, 255).trim();
        String safe = trimmed.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return safe;
    }

    public Path resolveAndValidate(String relativePath) {
        ensureInStorageRoot(relativePath);
        return properties.storageRootPath().resolve(relativePath).normalize();
    }

    public void ensureExists(Path filePath) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("共享存储中未找到上传文件，请确认上传已完成");
        }
    }

    public void createParentDirectory(Path absolutePath) {
        try {
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("创建共享目录失败: " + e.getMessage(), e);
        }
    }

    public long fileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件大小失败: " + e.getMessage(), e);
        }
    }

    public String sha256(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("计算文件哈希失败: " + e.getMessage(), e);
        }
    }

    private void ensureInStorageRoot(String relativePath) {
        Path resolved = properties.storageRootPath().resolve(relativePath).normalize();
        if (!resolved.startsWith(properties.storageRootPath())) {
            throw new IllegalArgumentException("非法文件路径");
        }
    }
}
