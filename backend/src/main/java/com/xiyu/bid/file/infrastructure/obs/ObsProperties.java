package com.xiyu.bid.file.infrastructure.obs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 华为云 OBS 配置绑定。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "xiyu.obs")
public class ObsProperties {

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
     * 是否使用 IAM 委托（AssumeAgency）换取临时凭证。
     * 未配置 agencyName 或 domainName 时退化为 AK/SK 直传模式。
     */
    public boolean isAgencyMode() {
        return agencyName != null && !agencyName.isBlank()
                && domainName != null && !domainName.isBlank();
    }
}
