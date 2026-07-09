package com.xiyu.bid.file.application;

import com.obs.services.ObsClient;
import com.obs.services.model.TemporarySignatureRequest;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.entity.BidFile;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.dto.DownloadUrlResponse;
import com.xiyu.bid.file.infrastructure.obs.ObsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.io.IOException;

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
            throw new BusinessException(409, "文件尚未处理完成，当前状态: " + bidFile.getStatus());
        }

        int effectiveExpireSeconds = Math.min(expireSeconds, 3600);
        effectiveExpireSeconds = Math.max(effectiveExpireSeconds, 60);

        try (ObsClient client = new ObsClient(
                obsProperties.getAccessKey(),
                obsProperties.getSecretKey(),
                obsProperties.getEndpoint())) {

            TemporarySignatureRequest request = new TemporarySignatureRequest(
                    com.obs.services.model.HttpMethodEnum.GET,
                    effectiveExpireSeconds);
            request.setBucketName(bidFile.getBucket());
            request.setObjectKey(bidFile.getObjectKey());

            String signedUrl = client.createTemporarySignature(request).getSignedUrl();

            return DownloadUrlResponse.builder()
                    .url(signedUrl)
                    .expiresAt(Instant.now().plusSeconds(effectiveExpireSeconds))
                    .build();
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("生成下载链接失败: " + e.getMessage(), e);
        }
    }
}
