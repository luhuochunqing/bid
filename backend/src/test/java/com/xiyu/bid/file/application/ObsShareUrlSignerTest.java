package com.xiyu.bid.file.application;

import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
import com.xiyu.bid.file.entity.BidFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ObsShareUrlSigner 单元测试。
 *
 * <p>覆盖 obs-direct:{uploadId} → OBS 预签名 URL 转换逻辑，
 * 确保 CRM 回调场景下能正确签发可访问的 OBS 分享链接。
 */
@DisplayName("ObsShareUrlSigner obs-direct URL 签发")
@ExtendWith(MockitoExtension.class)
class ObsShareUrlSignerTest {

    @Mock
    private BidFileRepository bidFileRepository;

    @Mock
    private ObsDownloadUrlGateway obsDownloadUrlGateway;

    @InjectMocks
    private ObsShareUrlSigner signer;

    private BidFile sampleBidFile;

    @BeforeEach
    void setUp() {
        sampleBidFile = BidFile.builder()
                .id(1L)
                .uploadId("upload-abc-123")
                .bucket("ehsy-widbid")
                .objectKey("bids/2026/07/test.pdf")
                .originalName("投标文件.pdf")
                .fileSize(1024000L)
                .creatorId(42L)
                .build();
    }

    @Test
    @DisplayName("obs-direct:{uploadId} → 成功签发 OBS 预签名 URL")
    void trySign_obsDirectUrl_returnsPresignedUrl() {
        when(bidFileRepository.findByUploadId("upload-abc-123"))
                .thenReturn(Optional.of(sampleBidFile));
        when(obsDownloadUrlGateway.signDownloadUrl("ehsy-widbid", "bids/2026/07/test.pdf", 3600))
                .thenReturn(new SignedDownloadUrl(
                        "https://widbid-obs.ehsy.com/bids/2026/07/test.pdf?AccessKeyId=xxx&Signature=yyy",
                        Instant.now().plusSeconds(3600)));

        Optional<String> result = signer.trySign("obs-direct:upload-abc-123");

        assertThat(result).isPresent();
        assertThat(result.get()).startsWith("https://widbid-obs.ehsy.com/");
        assertThat(result.get()).contains("bids/2026/07/test.pdf");
    }

    @Test
    @DisplayName("非 obs-direct: 前缀的 URL 原样返回")
    void trySign_nonObsDirectUrl_returnsAsIs() {
        Optional<String> result = signer.trySign("doc-insight://TENDER_INTAKE/test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("doc-insight://TENDER_INTAKE/test.pdf");
        verify(bidFileRepository, never()).findByUploadId(anyString());
        verify(obsDownloadUrlGateway, never()).signDownloadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("http(s):// URL 原样返回，不调用 OBS 签发")
    void trySign_httpUrl_returnsAsIs() {
        Optional<String> result = signer.trySign("https://example.com/test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("https://example.com/test.pdf");
        verify(bidFileRepository, never()).findByUploadId(anyString());
    }

    @Test
    @DisplayName("null 或空字符串返回 Optional.empty()")
    void trySign_nullOrBlank_returnsEmpty() {
        assertThat(signer.trySign(null)).isEmpty();
        assertThat(signer.trySign("")).isEmpty();
        assertThat(signer.trySign("   ")).isEmpty();
    }

    @Test
    @DisplayName("obs-direct: 缺少 uploadId 返回 Optional.empty()")
    void trySign_obsDirectWithoutUploadId_returnsEmpty() {
        Optional<String> result = signer.trySign("obs-direct:");

        assertThat(result).isEmpty();
        verify(bidFileRepository, never()).findByUploadId(anyString());
    }

    @Test
    @DisplayName("BidFile 不存在返回 Optional.empty()，不抛异常")
    void trySign_bidFileNotFound_returnsEmpty() {
        when(bidFileRepository.findByUploadId("non-existent"))
                .thenReturn(Optional.empty());

        Optional<String> result = signer.trySign("obs-direct:non-existent");

        assertThat(result).isEmpty();
        verify(obsDownloadUrlGateway, never()).signDownloadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("OBS Gateway 抛异常时返回 Optional.empty()，不向上传播")
    void trySign_gatewayThrows_returnsEmpty() {
        when(bidFileRepository.findByUploadId("upload-abc-123"))
                .thenReturn(Optional.of(sampleBidFile));
        when(obsDownloadUrlGateway.signDownloadUrl(anyString(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("OBS 直传未启用"));

        Optional<String> result = signer.trySign("obs-direct:upload-abc-123");

        assertThat(result).isEmpty();
    }
}
