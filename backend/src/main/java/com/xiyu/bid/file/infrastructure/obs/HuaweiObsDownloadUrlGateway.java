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
 *
 * <p>自定义域名支持：若配置了 {@code downloadCustomDomain}（如 widbid-obs.ehsy.com），
 * 会创建专用的 {@code downloadObsClient}，以自定义域名作为 endpoint 生成预签名 URL。
 * URL 格式为 {@code {bucket}.{custom-domain}/{object-key}}（virtual-hosted-style）。
 * 使用自定义域名前，必须在华为云 OBS 控制台为该域名上传/绑定 SSL 证书，
 * 否则客户端会报 TLS/SSL 证书错误。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuaweiObsDownloadUrlGateway implements ObsDownloadUrlGateway {

    private final ObsProperties obsProperties;
    private ObsClient obsClient;
    /** 下载专用 ObsClient（使用自定义域名作为 endpoint），为 null 时复用 obsClient。 */
    private ObsClient downloadObsClient;

    @PostConstruct
    void initObsClient() {
        if (obsProperties.isEnabled()) {
            this.obsClient = new ObsClient(
                    obsProperties.getAccessKey(),
                    obsProperties.getSecretKey(),
                    obsProperties.getEndpoint());
            // 配置了自定义域名时，创建专用下载 ObsClient
            String customDomain = obsProperties.getDownloadCustomDomain();
            if (customDomain != null && !customDomain.isBlank()) {
                String downloadEndpoint = customDomain.startsWith("http")
                        ? customDomain
                        : "https://" + customDomain;
                this.downloadObsClient = new ObsClient(
                        obsProperties.getAccessKey(),
                        obsProperties.getSecretKey(),
                        downloadEndpoint);
                log.info("OBS 下载自定义域名已启用: {} (endpoint={})", customDomain, downloadEndpoint);
            }
        }
    }

    @PreDestroy
    void closeObsClient() {
        closeQuietly(obsClient);
        closeQuietly(downloadObsClient);
    }

    private void closeQuietly(ObsClient client) {
        if (client != null) {
            try {
                client.close();
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

        // 优先使用自定义域名 ObsClient 生成预签名 URL
        ObsClient signer = downloadObsClient != null ? downloadObsClient : obsClient;
        String signedUrl = signer.createTemporarySignature(request).getSignedUrl();

        return new SignedDownloadUrl(
                signedUrl,
                Instant.now().plusSeconds(expireSeconds));
    }
}
