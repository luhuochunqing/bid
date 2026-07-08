package com.xiyu.bid.file.infrastructure.obs;

import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.TemporarySignatureRequest;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuaweiObsDownloadUrlGateway implements ObsDownloadUrlGateway {

    private final ObsProperties obsProperties;

    @Override
    public SignedDownloadUrl signDownloadUrl(String bucket, String objectKey, int expireSeconds) {
        if (!obsProperties.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        try (ObsClient client = new ObsClient(
                obsProperties.getAccessKey(),
                obsProperties.getSecretKey(),
                obsProperties.getEndpoint())) {

            TemporarySignatureRequest request = new TemporarySignatureRequest(
                    HttpMethodEnum.GET,
                    expireSeconds);
            request.setBucketName(bucket);
            request.setObjectKey(objectKey);

            String signedUrl = client.createTemporarySignature(request).getSignedUrl();

            return new SignedDownloadUrl(
                    signedUrl,
                    Instant.now().plusSeconds(expireSeconds));
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("生成下载链接失败: " + e.getMessage(), e);
        }
    }
}
