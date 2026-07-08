package com.xiyu.bid.file.application;

import com.huaweicloud.sdk.obs.v1.ObsClient;
import com.huaweicloud.sdk.obs.v1.model.CreateSignedUrlRequest;
import com.huaweicloud.sdk.obs.v1.model.TemporarySignatureRequest;
import com.xiyu.bid.file.domain.BidFile;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.dto.DownloadUrlResponse;
import com.xiyu.bid.file.infrastructure.obs.ObsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * 获取 OBS 预签名下载 URL 用例。
 */
@Service
@RequiredArgsConstructor
public class GetDownloadUrlUseCase {

    private final ObsProperties obsProperties;
    private final BidFileRepository bidFileRepository;

    public DownloadUrlResponse execute(String uploadId, int expireSeconds, Long operatorId) {
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传记录不存在"));

        if (!Objects.equals(bidFile.getCreatorId(), operatorId)) {
            throw new SecurityException("无权访问该文件");
        }

        if (bidFile.getStatus() != BidFileStatus.COMPLETED) {
            throw new IllegalStateException("文件尚未处理完成，当前状态: " + bidFile.getStatus());
        }

        int effectiveExpireSeconds = Math.min(expireSeconds, 3600);
        effectiveExpireSeconds = Math.max(effectiveExpireSeconds, 60);

        try (ObsClient client = new ObsClient(
                obsProperties.getAccessKey(),
                obsProperties.getSecretKey(),
                obsProperties.getEndpoint())) {

            TemporarySignatureRequest request = new TemporarySignatureRequest(
                    com.huaweicloud.sdk.obs.v1.model.HttpMethodEnum.GET,
                    effectiveExpireSeconds);
            request.setBucketName(bidFile.getBucket());
            request.setObjectKey(bidFile.getObjectKey());

            CreateSignedUrlResponse response = client.createSignedUrl(request);

            return DownloadUrlResponse.builder()
                    .url(response.getSignedUrl())
                    .expiresAt(Instant.now().plusSeconds(effectiveExpireSeconds))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("生成下载链接失败: " + e.getMessage(), e);
        }
    }
}
