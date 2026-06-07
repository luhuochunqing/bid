package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTenderDocumentStorageTest {

    @TempDir
    private Path tempDir;

    @Test
    void loadByFileUrl_shouldReadDocumentPreviouslyStoredByFileUrl() {
        LocalTenderDocumentStorage storage = new LocalTenderDocumentStorage(tempDir.toString());
        byte[] content = "招标正文".getBytes(StandardCharsets.UTF_8);

        var stored = storage.store(11L, "招标文件.docx", "application/octet-stream", content);
        var loaded = storage.loadByFileUrl(stored.fileUrl());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().storedDocument().fileUrl()).isEqualTo(stored.fileUrl());
        assertThat(loaded.get().storedDocument().contentSha256()).isEqualTo(stored.contentSha256());
        assertThat(loaded.get().content()).isEqualTo(content);
    }

    @Test
    void loadByFileUrl_shouldReadDocInsightTenderIntakeFiles() throws Exception {
        Path docInsightRoot = tempDir.resolve("doc-insight");
        Path storedPath = docInsightRoot
                .resolve("TENDER_INTAKE")
                .resolve("manual-tender")
                .resolve("hash-招标文件.pdf");
        byte[] content = "首次上传正文".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(storedPath.getParent());
        Files.write(storedPath, content);
        LocalTenderDocumentStorage storage = new LocalTenderDocumentStorage(
                tempDir.resolve("bid-agent").toString(),
                docInsightRoot.toString()
        );

        var loaded = storage.loadByFileUrl("doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().storedDocument().fileUrl())
                .isEqualTo("doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf");
        assertThat(loaded.get().content()).isEqualTo(content);
    }
}
