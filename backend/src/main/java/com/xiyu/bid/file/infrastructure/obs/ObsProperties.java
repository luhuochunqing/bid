package com.xiyu.bid.file.infrastructure.obs;

import com.xiyu.bid.file.domain.gateway.ObsUploadConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 华为云 OBS 配置绑定。
 *
 * <p>D2-1 修复：实现 {@link ObsUploadConfig} 端口接口，
 * application 层通过接口依赖，不直接依赖本配置类。</p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "xiyu.obs")
public class ObsProperties implements ObsUploadConfig {

    private boolean enabled = false;

    private String endpoint;

    private String region;

    private String bucket;

    private String accessKey;

    private String secretKey;

    private String agencyName;

    private String domainName;

    private Integer tokenDurationSeconds = 3600;

    private String objectKeyPrefix = "bids";

    private List<String> allowedActions = List.of(
            "obs:object:PutObject",
            "obs:object:GetObject",
            "obs:object:DeleteObject",
            "obs:bucket:ListBucketMultipartUploads");

    private Integer downloadUrlExpireSeconds = 300;

    /**
     * 下载预签名 URL 使用的自定义域名（如 widbid-obs.ehsy.com）。
     *
     * <p>用户在华为云 OBS 控制台为桶绑定的自定义域名。配置后，
     * {@link HuaweiObsDownloadUrlGateway} 会用此域名作为 endpoint 生成预签名 URL，
     * 使外部系统（如 CRM）可直接通过该域名访问 OBS 文件，无需经过 XiYu 后端代理。</p>
     *
     * <p>为空时退化为标准 OBS endpoint（如 obs.cn-east-3.myhuaweicloud.com）。</p>
     */
    private String downloadCustomDomain;

    /**
     * 是否使用 IAM 委托（AssumeAgency）换取临时凭证。
     * 未配置 agencyName 或 domainName 时退化为 AK/SK 直传模式。
     */
    public boolean isAgencyMode() {
        return agencyName != null && !agencyName.isBlank()
                && domainName != null && !domainName.isBlank();
    }
}
