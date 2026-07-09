package com.xiyu.bid.file.infrastructure.obs;

import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.TemporarySignatureRequest;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

/**
 * 华为云 OBS 预签名下载 URL 适配器。
 *
 * <p>实现 {@link ObsDownloadUrlGateway} 端口接口，封装 OBS SDK 访问细节。
 * application 层依赖接口而非直接 new ObsClient，避免 SDK 泄漏（R1 修复）。</p>
 *
 * <p>D4-1 修复：ObsClient 改为单例复用，@PostConstruct 初始化，@PreDestroy 关闭。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuaweiObsDownloadUrlGateway implements ObsDownloadUrlGateway {

    private final ObsProperties obsProperties;
    private ObsClient obsClient;

    @PostConstruct
    void initObsClient() {
        if (obsProperties.isEnabled()) {
            this.obsClient = new ObsClient(
                    obsProperties.getAccessKey(),
                    obsProperties.getSecretKey(),
                    obsProperties.getEndpoint());
        }
    }

    @PreDestroy
    void closeObsClient() {
        if (obsClient != null) {
            try {
                obsClient.close();
            } catch (IOException | RuntimeException e) {
                log.warn("关闭 ObsClient 失败", e);
            }
        }
    }

    @Override
    public SignedDownloadUrl signDownloadUrl(String bucket, String objectKey, int expireSeconds) {
        if (!obsProperties.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        TemporarySignatureRequest request = new TemporarySignatureRequest(
                HttpMethodEnum.GET,
                expireSeconds);
        request.setBucketName(bucket);
        request.setObjectKey(objectKey);

        String signedUrl = obsClient.createTemporarySignature(request).getSignedUrl();

        return new SignedDownloadUrl(
                signedUrl,
                Instant.now().plusSeconds(expireSeconds));
    }
}
