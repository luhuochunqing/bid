package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.projectworkflow.service.LoadedProjectDocumentFile;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.projectworkflow.service.StoredProjectDocumentFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class BidAgentProjectDocumentFileStorage implements ProjectDocumentFileStorage {

    private final TenderDocumentStorage tenderDocumentStorage;

    @Override
    public StoredProjectDocumentFile store(Long projectId, String fileName, String contentType, byte[] content) {
        var stored = tenderDocumentStorage.store(projectId, fileName, contentType, content);
        return new StoredProjectDocumentFile(stored.fileUrl(), stored.storagePath());
    }

    @Override
    public Optional<LoadedProjectDocumentFile> load(String fileUrl) {
        return tenderDocumentStorage.loadByFileUrl(fileUrl)
                .map(loaded -> {
                    String storagePath = loaded.storedDocument().storagePath();
                    byte[] content = loaded.content() == null ? new byte[0] : loaded.content();
                    return new LoadedProjectDocumentFile(
                            loaded.storedDocument().fileUrl(),
                            storagePath,
                            null,
                            content,
                            resolveResource(storagePath, content)
                    );
                });
    }

    private Resource resolveResource(String storagePath, byte[] content) {
        if (storagePath != null) {
            Path path = Path.of(storagePath);
            if (Files.isRegularFile(path)) {
                return new FileSystemResource(path);
            }
        }
        return new ByteArrayResource(content);
    }
}
