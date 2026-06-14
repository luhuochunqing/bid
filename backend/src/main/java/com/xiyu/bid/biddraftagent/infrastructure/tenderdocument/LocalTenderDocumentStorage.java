package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * @deprecated 已由 {@link com.xiyu.bid.docinsight.infrastructure.storage.LocalDocumentStorage} 取代。
 *             新代码请使用 docinsight 模块的存储实现。将在 next-release 移除。
 */
@Deprecated(since = "next-release", forRemoval = true)
@Profile("dev")
@Component
@ConditionalOnMissingBean(TenderDocumentStorage.class)
public class LocalTenderDocumentStorage implements TenderDocumentStorage {

    private static final String FILE_URL_PREFIX = "bid-agent://tender-documents/";
    private static final String DOC_INSIGHT_FILE_URL_PREFIX = "doc-insight://";

    private final Path uploadRoot;
    private final Path docInsightUploadRoot;

    @Autowired
    public LocalTenderDocumentStorage(
            @Value("${app.bid-agent.upload-dir:}") String configuredUploadDir,
            @Value("${app.doc-insight.upload-dir:}") String configuredDocInsightUploadDir) {
        this.uploadRoot = configuredUploadDir == null || configuredUploadDir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "xiyu-bid-agent-uploads")
                : Path.of(configuredUploadDir);
        this.docInsightUploadRoot = configuredDocInsightUploadDir == null || configuredDocInsightUploadDir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "xiyu-doc-insight-uploads")
                : Path.of(configuredDocInsightUploadDir);
    }

    LocalTenderDocumentStorage(String configuredUploadDir) {
        this(configuredUploadDir, "");
    }

    @Override
    public StoredTenderDocument store(Long projectId, String fileName, String contentType, byte[] content) {
        String hash = sha256(content);
        String safeName = safeFileName(fileName);
        Path projectDir = uploadRoot.resolve(String.valueOf(projectId));
        Path targetPath = projectDir.resolve(hash.substring(0, 12) + "-" + safeName);
        try {
            Files.createDirectories(projectDir);
            Files.write(targetPath, content);
        } catch (IOException ex) {
            throw new IllegalStateException("保存招标文件失败", ex);
        }
        return new StoredTenderDocument(
                "bid-agent://tender-documents/" + projectId + "/" + targetPath.getFileName(),
                targetPath.toAbsolutePath().toString(),
                hash
        );
    }

    @Override
    public Optional<LoadedTenderDocument> loadByFileUrl(String fileUrl) {
        if (fileUrl == null) {
            return Optional.empty();
        }
        if (fileUrl.startsWith(DOC_INSIGHT_FILE_URL_PREFIX)) {
            return loadFromRoot(fileUrl, DOC_INSIGHT_FILE_URL_PREFIX, docInsightUploadRoot);
        }
        if (!fileUrl.startsWith(FILE_URL_PREFIX)) {
            return Optional.empty();
        }
        return loadFromRoot(fileUrl, FILE_URL_PREFIX, uploadRoot);
    }

    private Optional<LoadedTenderDocument> loadFromRoot(String fileUrl, String prefix, Path configuredRoot) {
        String relativePath = fileUrl.substring(prefix.length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            return Optional.empty();
        }
        Path root = configuredRoot.toAbsolutePath().normalize();
        Path targetPath = root.resolve(relativePath).normalize();
        if (!targetPath.startsWith(root) || !Files.isRegularFile(targetPath)) {
            return Optional.empty();
        }
        try {
            byte[] content = Files.readAllBytes(targetPath);
            StoredTenderDocument storedDocument = new StoredTenderDocument(
                    fileUrl,
                    targetPath.toAbsolutePath().toString(),
                    sha256(content)
            );
            return Optional.of(new LoadedTenderDocument(storedDocument, content));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private String safeFileName(String fileName) {
        String candidate = fileName == null || fileName.isBlank() ? "tender-document" : fileName.trim();
        return candidate.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
