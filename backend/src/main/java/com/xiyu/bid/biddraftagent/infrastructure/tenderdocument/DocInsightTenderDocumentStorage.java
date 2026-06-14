package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.StoredTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.docinsight.application.DocumentStorage;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 基于 docinsight 模块 {@link DocumentStorage} 的招标文件存储适配器。
 */
@Component
@Primary
class DocInsightTenderDocumentStorage implements TenderDocumentStorage {

    private static final String FILE_URL_PREFIX = "doc-insight://";
    private static final String STORAGE_CATEGORY = "tender-file";

    private final DocumentStorage documentStorage;
    private final Path uploadRoot;

    DocInsightTenderDocumentStorage(
            DocumentStorage documentStorage,
            @Value("${app.doc-insight.upload-dir:}") String configuredUploadDir) {
        this.documentStorage = documentStorage;
        this.uploadRoot = configuredUploadDir == null || configuredUploadDir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "xiyu-doc-insight-uploads")
                : Path.of(configuredUploadDir);
    }

    @Override
    public StoredTenderDocument store(Long projectId, String fileName, String contentType, byte[] content) {
        com.xiyu.bid.docinsight.application.StoredDocument stored = documentStorage.store(
                STORAGE_CATEGORY,
                String.valueOf(projectId),
                fileName,
                contentType,
                content
        );
        return new StoredTenderDocument(stored.fileUrl(), stored.storagePath(), stored.contentSha256());
    }

    @Override
    public Optional<LoadedTenderDocument> loadByFileUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(FILE_URL_PREFIX)) {
            return Optional.empty();
        }
        String relativePath = fileUrl.substring(FILE_URL_PREFIX.length());
        if (relativePath.isBlank() || relativePath.contains("..")) {
            return Optional.empty();
        }
        Path root = uploadRoot.toAbsolutePath().normalize();
        Path targetPath = root.resolve(relativePath).normalize();
        if (!targetPath.startsWith(root)) {
            return Optional.empty();
        }
        String storagePath = targetPath.toAbsolutePath().toString();
        return documentStorage.load(storagePath)
                .flatMap(content -> documentStorage.lookup(storagePath)
                        .map(stored -> new LoadedTenderDocument(
                                new StoredTenderDocument(stored.fileUrl(), stored.storagePath(), stored.contentSha256()),
                                content
                        )));
    }
}
