package com.xiyu.bid.file.domain;

import com.xiyu.bid.file.entity.BidFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DownloadPolicy} 单元测试。
 */
class DownloadPolicyTest {

    private final DownloadPolicy policy = new DownloadPolicy();

    private BidFile bidFile;

    @BeforeEach
    void setUp() {
        bidFile = BidFile.builder()
                .id(1L)
                .uploadId("upload-123")
                .status(BidFileStatus.COMPLETED)
                .originalName("test.pdf")
                .objectKey("bids/2026/07/upload-123/test.pdf")
                .bucket("test-bucket")
                .fileSize(1024L)
                .creatorId(100L)
                .build();
    }

    @Test
    void validateDownload_success_whenCompletedAndOwner() {
        ValidationResult result = policy.validateDownload(bidFile, 100L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void validateDownload_failure_whenNotOwner() {
        ValidationResult result = policy.validateDownload(bidFile, 999L);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("无权");
    }

    @Test
    void validateDownload_failure_whenNotCompleted() {
        bidFile = BidFile.builder()
                .uploadId("upload-456")
                .status(BidFileStatus.UPLOADING)
                .originalName("test.pdf")
                .objectKey("bids/2026/07/upload-456/test.pdf")
                .bucket("test-bucket")
                .fileSize(1024L)
                .creatorId(100L)
                .build();

        ValidationResult result = policy.validateDownload(bidFile, 100L);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.reason()).contains("尚未处理完成");
    }

    @Test
    void validateDownload_failure_whenFailed() {
        bidFile.fail("之前的错误");

        ValidationResult result = policy.validateDownload(bidFile, 100L);

        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void clampExpireSeconds_returnsValueAsIs_whenInRange() {
        assertThat(policy.clampExpireSeconds(300)).isEqualTo(300);
        assertThat(policy.clampExpireSeconds(60)).isEqualTo(60);
        assertThat(policy.clampExpireSeconds(3600)).isEqualTo(3600);
    }

    @Test
    void clampExpireSeconds_clampsToMin_whenBelowRange() {
        assertThat(policy.clampExpireSeconds(0)).isEqualTo(60);
        assertThat(policy.clampExpireSeconds(30)).isEqualTo(60);
        assertThat(policy.clampExpireSeconds(59)).isEqualTo(60);
    }

    @Test
    void clampExpireSeconds_clampsToMax_whenAboveRange() {
        assertThat(policy.clampExpireSeconds(3601)).isEqualTo(3600);
        assertThat(policy.clampExpireSeconds(7200)).isEqualTo(3600);
        assertThat(policy.clampExpireSeconds(99999)).isEqualTo(3600);
    }

    @Test
    void clampExpireSeconds_clampsNegativeToMin() {
        assertThat(policy.clampExpireSeconds(-1)).isEqualTo(60);
        assertThat(policy.clampExpireSeconds(-1000)).isEqualTo(60);
    }
}
