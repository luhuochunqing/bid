package com.xiyu.bid.file.infrastructure.obs;

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
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
 * 会创建专用的 {@code downloadObsClient}，通过 {@link ObsConfiguration#setCname(boolean)}
 * 将 endpoint 标记为 CNAME/自定义域名，生成 {@code {custom-domain}/{object-key}}
 * 格式的预签名 URL（不带 bucket 前缀），与华为云 OBS 控制台生成的分享链接一致。
 * 使用自定义域名前，必须在 OBS 控制台完成：1）绑定自定义域名；2）配置 CNAME；
 * 3）上传/绑定 SSL 证书。</p>
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
            // 配置了自定义域名时，创建专用下载 ObsClient（CNAME 模式）
            String customDomain = obsProperties.getDownloadCustomDomain();
            if (customDomain != null && !customDomain.isBlank()) {
                String domain = customDomain.startsWith("http")
                        ? java.net.URI.create(customDomain).getHost()
                        : customDomain;
                ObsConfiguration downloadConfig = new ObsConfiguration();
                downloadConfig.setEndPoint(domain);
                downloadConfig.setHttpsOnly(true);
                downloadConfig.setCname(true);
                this.downloadObsClient = new ObsClient(
                        obsProperties.getAccessKey(),
                        obsProperties.getSecretKey(),
                        downloadConfig);
                log.info("OBS 下载自定义域名已启用 (CNAME): {}", domain);
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

        // SDK 在 CNAME 模式下会显式输出 :443 默认端口，去掉以匹配控制台 URL 格式
        if (downloadObsClient != null) {
            signedUrl = stripDefaultPort(signedUrl);
        }

        return new SignedDownloadUrl(
                signedUrl,
                Instant.now().plusSeconds(expireSeconds));
    }

    private static String stripDefaultPort(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceFirst("^(https://[^/]+):443/", "$1/")
                .replaceFirst("^(http://[^/]+):80/", "$1/");
    }
}
