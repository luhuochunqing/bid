package com.xiyu.bid.file.domain;

import com.xiyu.bid.file.entity.BidFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UploadCompletionPolicy} 单元测试。
 */
class UploadCompletionPolicyTest {

    private final UploadCompletionPolicy policy = new UploadCompletionPolicy();

    private BidFile bidFile;

    @BeforeEach
    void setUp() {
        bidFile = BidFile.builder()
                .id(1L)
                .uploadId("upload-123")
                .status(BidFileStatus.UPLOADING)
                .originalName("test.pdf")
                .objectKey("bids/2026/07/upload-123/test.pdf")
                .bucket("test-bucket")
                .fileSize(1024L)
                .fileHash("abc123")
                .creatorId(100L)
                .build();
    }

    @Test
    void validateOwnership_success_whenCreatorMatchesOperator() {
        ValidationResult result = policy.validateOwnership(bidFile, 100L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void validateOwnership_failure_whenOperatorMismatch() {
        ValidationResult result = policy.validateOwnership(bidFile, 999L);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("无权");
    }

    @Test
    void validateStatusTransition_success_whenUploading() {
        ValidationResult result = policy.validateStatusTransition(bidFile);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateStatusTransition_failure_whenAlreadyCompleted() {
        bidFile.transitionTo(BidFileStatus.COMPLETED);

        ValidationResult result = policy.validateStatusTransition(bidFile);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("状态不正确");
    }

    @Test
    void validateStatusTransition_failure_whenFailed() {
        bidFile.fail("之前的错误");

        ValidationResult result = policy.validateStatusTransition(bidFile);

        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void validateSize_success_whenSizesMatch() {
        ValidationResult result = policy.validateSize(1024L, 1024L);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateSize_failure_whenActualIsNull() {
        ValidationResult result = policy.validateSize(1024L, null);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("不存在");
    }

    @Test
    void validateSize_failure_whenSizeMismatch() {
        ValidationResult result = policy.validateSize(1024L, 2048L);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("大小不匹配");
        assertThat(result.reason()).contains("1024");
        assertThat(result.reason()).contains("2048");
    }

    @Test
    void validateEtag_success_whenBothNull() {
        ValidationResult result = policy.validateEtag(null, null);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateEtag_success_whenExpectedNull() {
        ValidationResult result = policy.validateEtag(null, "some-etag");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateEtag_success_whenActualNull() {
        ValidationResult result = policy.validateEtag("expected", null);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateEtag_success_whenMatch() {
        ValidationResult result = policy.validateEtag("abc123", "abc123");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateEtag_success_whenMatchIgnoreCase() {
        ValidationResult result = policy.validateEtag("ABC123", "abc123");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateEtag_failure_whenMismatch() {
        ValidationResult result = policy.validateEtag("abc123", "xyz789");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("ETag");
    }
}
