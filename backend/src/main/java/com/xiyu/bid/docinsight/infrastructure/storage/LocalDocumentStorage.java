package com.xiyu.bid.docinsight.infrastructure.storage;

import com.xiyu.bid.docinsight.application.DocumentStorage;
import com.xiyu.bid.docinsight.application.StoredDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class LocalDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorage.class);

    private final Path uploadRoot;

    public LocalDocumentStorage(@Value("${app.doc-insight.upload-dir:}") String configuredUploadDir) {
        this.uploadRoot = configuredUploadDir == null || configuredUploadDir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "xiyu-doc-insight-uploads")
                : Path.of(configuredUploadDir);
    }

    @Override
    public StoredDocument store(String category, String entityId, String fileName, String contentType, byte[] content) {
        String hash = sha256(content);
        String safeName = safeFileName(fileName);
        Path targetDir = uploadRoot.resolve(category).resolve(entityId);
        Path targetPath = targetDir.resolve(hash.substring(0, 12) + "-" + safeName);
        
        try {
            Files.createDirectories(targetDir);
            Files.write(targetPath, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store document locally", ex);
        }

        return new StoredDocument(
                "doc-insight://" + category + "/" + entityId + "/" + targetPath.getFileName(),
                targetPath.toAbsolutePath().toString(),
                hash
        );
    }

    @Override
    public Optional<byte[]> load(String storagePath) {
        try {
            Path path = Path.of(storagePath);
            if (Files.exists(path)) {
                return Optional.of(Files.readAllBytes(path));
            }
        } catch (IOException e) {
            log.warn("Failed to load document from {}: {}", storagePath, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 根据绝对存储路径推导 StoredDocument 元数据。
     * fileUrl 与 store() 保持一致（doc-insight://{uploadRoot 的相对路径}）。
     * contentHash 通过读取文件内容实时计算。
     * 注意：此方法会重新读取文件，如已调用 load() 则存在双读，待元数据持久化后可优化。
     */
    @Override
    public Optional<StoredDocument> lookup(String storagePath) {
        try {
            Path path = Path.of(storagePath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            byte[] content = Files.readAllBytes(path);
            String hash = sha256(content);
            Path relative = uploadRoot.toAbsolutePath().relativize(path.toAbsolutePath());
            String fileUrl = "doc-insight://" + relative.toString().replace('\\', '/');
            return Optional.of(new StoredDocument(fileUrl, storagePath, hash));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String safeFileName(String fileName) {
        String candidate = fileName == null || fileName.isBlank() ? "document" : fileName.trim();
        return candidate.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
