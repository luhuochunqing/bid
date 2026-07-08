package com.xiyu.bid.file.application;

import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.DownloadPolicy;
import com.xiyu.bid.file.domain.ValidationResult;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
import com.xiyu.bid.file.dto.DownloadUrlResponse;
import com.xiyu.bid.file.entity.BidFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 获取 OBS 预签名下载 URL 用例（应用编排层）。
 *
 * <p>职责仅限编排：取数、调 {@link DownloadPolicy} 做校验与 clamp、
 * 调 {@link ObsDownloadUrlGateway} 生成签名 URL、组装响应。
 * 不再直接 new ObsClient（R1 SDK 泄漏修复）。</p>
 */
@Service
@RequiredArgsConstructor
public class GetDownloadUrlUseCase {

    private final BidFileRepository bidFileRepository;
    private final ObsDownloadUrlGateway obsDownloadUrlGateway;
    private final DownloadPolicy downloadPolicy;

    public DownloadUrlResponse execute(String uploadId, int expireSeconds, Long operatorId) {
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传记录不存在"));

        ValidationResult validation = downloadPolicy.validateDownload(bidFile, operatorId);
        if (validation.isFailure()) {
            // 保持 API 契约：归属失败抛 SecurityException，状态失败抛 BusinessException(409)
            if (!Objects.equals(bidFile.getCreatorId(), operatorId)) {
                throw new SecurityException(validation.reason());
            }
            throw new BusinessException(409, validation.reason());
        }

        int effectiveExpireSeconds = downloadPolicy.clampExpireSeconds(expireSeconds);

        SignedDownloadUrl signed = obsDownloadUrlGateway.signDownloadUrl(
                bidFile.getBucket(), bidFile.getObjectKey(), effectiveExpireSeconds);

        return DownloadUrlResponse.builder()
                .url(signed.url())
                .expiresAt(signed.expiresAt())
                .build();
    }
}
