package com.xiyu.bid.file.application;

import com.xiyu.bid.file.entity.BidFile;
import com.xiyu.bid.file.domain.BidFileRepository;
import com.xiyu.bid.file.domain.BidFileStatus;
import com.xiyu.bid.file.dto.UploadTokenRequest;
import com.xiyu.bid.file.dto.UploadTokenResponse;
import com.xiyu.bid.file.infrastructure.obs.HuaweiObsTokenService;
import com.xiyu.bid.file.infrastructure.obs.ObsProperties;
import com.xiyu.bid.file.infrastructure.obs.HuaweiObsTokenService.TemporaryCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 签发 OBS 上传凭证用例。
 */
@Service
@RequiredArgsConstructor
public class IssueUploadTokenUseCase {

    private final ObsProperties obsProperties;
    private final BidFileRepository bidFileRepository;
    private final HuaweiObsTokenService tokenService;

    @Transactional
    public UploadTokenResponse execute(UploadTokenRequest request, Long creatorId) {
        if (!obsProperties.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        String uploadId = UUID.randomUUID().toString();
        String objectKey = buildObjectKey(uploadId, request.fileName());

        BidFile bidFile = BidFile.builder()
                .uploadId(uploadId)
                .status(BidFileStatus.UPLOADING)
                .originalName(request.fileName())
                .objectKey(objectKey)
                .bucket(obsProperties.getBucket())
                .fileSize(request.fileSize())
                .fileHash(request.fileHash())
                .mimeType(request.mimeType())
                .creatorId(creatorId)
                .build();
        bidFileRepository.save(bidFile);

        TemporaryCredentials credentials = tokenService.issueToken(uploadId);

        return UploadTokenResponse.builder()
                .uploadId(uploadId)
                .accessKey(credentials.accessKey())
                .secretKey(credentials.secretKey())
                .securityToken(credentials.securityToken())
                .expiresAt(credentials.expiresAt())
                .bucket(obsProperties.getBucket())
                .endpoint(obsProperties.getEndpoint())
                .region(obsProperties.getRegion())
                .objectKey(objectKey)
                .build();
    }

    private String buildObjectKey(String uploadId, String fileName) {
        LocalDateTime now = LocalDateTime.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String safeName = Paths.get(fileName).getFileName().toString();
        return String.format("%s/%s/%s/%s", obsProperties.getObjectKeyPrefix(), datePath, uploadId, safeName);
    }
}
