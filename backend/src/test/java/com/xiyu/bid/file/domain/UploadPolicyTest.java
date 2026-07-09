package com.xiyu.bid.file.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UploadPolicy} 单元测试。
 */
class UploadPolicyTest {

    private final UploadPolicy policy = new UploadPolicy();

    @Test
    void generateUploadId_returnsValidUuid() {
        String uploadId = policy.generateUploadId();

        assertThat(uploadId).isNotBlank();
        UUID parsed = UUID.fromString(uploadId);
        assertThat(parsed.toString()).isEqualTo(uploadId);
    }

    @Test
    void generateUploadId_returnsUniqueValues() {
        String a = policy.generateUploadId();
        String b = policy.generateUploadId();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void buildObjectKey_containsPrefixDatePathUploadIdAndSafeName() {
        String uploadId = "upload-123";
        String objectKey = policy.buildObjectKey("bids", uploadId, "test.pdf");

        String[] parts = objectKey.split("/");
        assertThat(parts).hasSize(5);
        assertThat(parts[0]).isEqualTo("bids");
        assertThat(parts[1]).matches("\\d{4}");
        assertThat(parts[2]).matches("\\d{2}");
        assertThat(parts[3]).isEqualTo(uploadId);
        assertThat(parts[4]).isEqualTo("test.pdf");
    }

    @Test
    void buildObjectKey_stripsDirectoryFromFileName() {
        String objectKey = policy.buildObjectKey("bids", "uid", "../../../etc/passwd");

        assertThat(objectKey).endsWith("/passwd");
        assertThat(objectKey).doesNotContain("..");
    }

    @Test
    void buildObjectKey_stripsWindowsPath() {
        String objectKey = policy.buildObjectKey("bids", "uid", "C:\\Users\\test\\file.pdf");

        assertThat(objectKey).endsWith("/file.pdf");
    }

    @Test
    void formatMonthPath_returnsYearSlashMonth() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 9, 10, 0);

        String path = policy.formatMonthPath(now);

        assertThat(path).isEqualTo("2026/07");
    }

    @Test
    void formatMonthPath_handlesJanuaryAndDecember() {
        assertThat(policy.formatMonthPath(LocalDateTime.of(2026, 1, 1, 0, 0))).isEqualTo("2026/01");
        assertThat(policy.formatMonthPath(LocalDateTime.of(2026, 12, 31, 23, 59))).isEqualTo("2026/12");
    }

    @Test
    void stripDirectoryPart_returnsFileNameWhenNoDirectory() {
        assertThat(policy.stripDirectoryPart("test.pdf")).isEqualTo("test.pdf");
    }

    @Test
    void stripDirectoryPart_stripsUnixPath() {
        assertThat(policy.stripDirectoryPart("/tmp/test.pdf")).isEqualTo("test.pdf");
        assertThat(policy.stripDirectoryPart("a/b/c/test.pdf")).isEqualTo("test.pdf");
    }

    @Test
    void stripDirectoryPart_stripsWindowsPath() {
        assertThat(policy.stripDirectoryPart("C:\\Users\\test.pdf")).isEqualTo("test.pdf");
    }

    @Test
    void stripDirectoryPart_returnsEmptyForNull() {
        assertThat(policy.stripDirectoryPart(null)).isEmpty();
    }

    @Test
    void stripDirectoryPart_returnsEmptyForEmpty() {
        assertThat(policy.stripDirectoryPart("")).isEmpty();
    }
}
