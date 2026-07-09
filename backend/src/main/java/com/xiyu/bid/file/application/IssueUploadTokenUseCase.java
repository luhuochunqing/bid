package com.xiyu.bid.file.application;

import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.domain.UploadPolicy;
import com.xiyu.bid.file.domain.gateway.ObsTokenGateway;
import com.xiyu.bid.file.domain.gateway.ObsUploadConfig;
import com.xiyu.bid.file.domain.model.TemporaryCredentials;
import com.xiyu.bid.file.dto.UploadTokenRequest;
import com.xiyu.bid.file.dto.UploadTokenResponse;
import com.xiyu.bid.file.entity.BidFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 签发 OBS 上传凭证用例（应用编排层）。
 *
 * <p>职责仅限编排：调 {@link UploadPolicy} 生成 uploadId/objectKey，
 * 持久化 {@link BidFile}，调 {@link ObsTokenGateway} 获取临时凭证，组装响应。
 * 不直接依赖 OBS SDK 实现类（R3 Hexagonal 边界修复）。
 * D1-1 修复：Policy 无状态，直接 new，删除 FilePolicyConfig。
 * D2-1 修复：通过 ObsUploadConfig 端口接口读取配置，不再直接依赖 ObsProperties。</p>
 */
@Service
@RequiredArgsConstructor
public class IssueUploadTokenUseCase {

    private final ObsUploadConfig obsUploadConfig;
    private final BidFileRepository bidFileRepository;
    private final ObsTokenGateway obsTokenGateway;
    private final UploadPolicy uploadPolicy = new UploadPolicy();

    @Transactional
    public UploadTokenResponse execute(UploadTokenRequest request, Long creatorId) {
        if (!obsUploadConfig.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        String uploadId = uploadPolicy.generateUploadId();
        String objectKey = uploadPolicy.buildObjectKey(
                obsUploadConfig.getObjectKeyPrefix(), uploadId, request.fileName());

        BidFile bidFile = BidFile.builder()
                .uploadId(uploadId)
                .status(BidFileStatus.UPLOADING)
                .originalName(request.fileName())
                .objectKey(objectKey)
                .bucket(obsUploadConfig.getBucket())
                .fileSize(request.fileSize())
                .fileHash(request.fileHash())
                .mimeType(request.mimeType())
                .creatorId(creatorId)
                .build();
        bidFileRepository.save(bidFile);

        TemporaryCredentials credentials = obsTokenGateway.issueToken(uploadId);

        return UploadTokenResponse.builder()
                .uploadId(uploadId)
                .accessKey(credentials.accessKey())
                .secretKey(credentials.secretKey())
                .securityToken(credentials.securityToken())
                .expiresAt(credentials.expiresAt())
                .bucket(obsUploadConfig.getBucket())
                .endpoint(obsUploadConfig.getEndpoint())
                .region(obsUploadConfig.getRegion())
                .objectKey(objectKey)
                .build();
    }
}
