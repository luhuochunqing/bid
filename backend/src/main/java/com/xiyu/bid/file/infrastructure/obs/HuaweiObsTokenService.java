package com.xiyu.bid.file.infrastructure.obs;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredential;
import com.huaweicloud.sdk.iam.v3.IamClient;
import com.huaweicloud.sdk.iam.v3.model.AgencyAuth;
import com.huaweicloud.sdk.iam.v3.model.AgencyAuthIdentity;
import com.huaweicloud.sdk.iam.v3.model.CreateTemporaryAccessKeyByAgencyRequest;
import com.huaweicloud.sdk.iam.v3.model.CreateTemporaryAccessKeyByAgencyRequestBody;
import com.huaweicloud.sdk.iam.v3.model.CreateTemporaryAccessKeyByAgencyResponse;
import com.huaweicloud.sdk.iam.v3.model.Credential;
import com.huaweicloud.sdk.iam.v3.model.IdentityAssumerole;
import com.huaweicloud.sdk.iam.v3.region.IamRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 华为云 OBS 临时凭证服务。
 *
 * <p>通过 IAM STS AssumeAgency 获取临时 AK/SK/SecurityToken，供前端直传 OBS 使用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuaweiObsTokenService {

    private final ObsProperties obsProperties;

    public TemporaryCredentials issueToken(String uploadId) {
        if (!obsProperties.isEnabled()) {
            throw new IllegalStateException("OBS 直传未启用");
        }

        // AK/SK 直传模式：未配置 IAM 委托时，直接返回永久 AK/SK（securityToken 为空）。
        // 注意：此模式会把 AK/SK 暴露给前端浏览器，仅在内网/可信场景下使用；
        // 生产环境强烈建议配置 IAM 委托走 STS 临时凭证。
        if (!obsProperties.isAgencyMode()) {
            log.warn("OBS 使用 AK/SK 直传模式，永久访问密钥将下发到前端，uploadId={}", uploadId);
            return new TemporaryCredentials(
                    obsProperties.getAccessKey(),
                    obsProperties.getSecretKey(),
                    null,
                    Instant.now().plusSeconds(obsProperties.getTokenDurationSeconds()));
        }

        ICredential auth = new BasicCredentials()
                .withAk(obsProperties.getAccessKey())
                .withSk(obsProperties.getSecretKey());

        IamClient client = IamClient.newBuilder()
                .withCredential(auth)
                .withRegion(IamRegion.valueOf(obsProperties.getRegion()))
                .build();

        try {
            IdentityAssumerole assumeRole = new IdentityAssumerole()
                    .withAgencyName(obsProperties.getAgencyName())
                    .withDomainName(obsProperties.getDomainName())
                    .withDurationSeconds(obsProperties.getTokenDurationSeconds())
                    .withSessionUser(new com.huaweicloud.sdk.iam.v3.model.AssumeroleSessionuser()
                            .withName("xiyu-bid-" + uploadId));

            AgencyAuthIdentity identity = new AgencyAuthIdentity()
                    .withMethods(List.of(AgencyAuthIdentity.MethodsEnum.ASSUME_ROLE))
                    .withAssumeRole(assumeRole);

            AgencyAuth agencyAuth = new AgencyAuth().withIdentity(identity);

            CreateTemporaryAccessKeyByAgencyRequestBody body =
                    new CreateTemporaryAccessKeyByAgencyRequestBody().withAuth(agencyAuth);

            CreateTemporaryAccessKeyByAgencyRequest request =
                    new CreateTemporaryAccessKeyByAgencyRequest().withBody(body);

            CreateTemporaryAccessKeyByAgencyResponse response =
                    client.createTemporaryAccessKeyByAgency(request);

            Credential creds = response.getCredential();

            if (creds == null) {
                throw new IllegalStateException("华为云 IAM 返回的临时凭证为空");
            }

            Instant expiresAt = parseExpiresAt(creds.getExpiresAt());

            return new TemporaryCredentials(
                    creds.getAccess(),
                    creds.getSecret(),
                    creds.getSecuritytoken(),
                    expiresAt);
        } catch (RuntimeException e) {
            log.error("签发华为云 OBS 临时凭证失败，uploadId={}", uploadId, e);
            throw new IllegalStateException("签发 OBS 临时凭证失败: " + e.getMessage(), e);
        }
    }

    private Instant parseExpiresAt(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return Instant.now().plusSeconds(obsProperties.getTokenDurationSeconds());
        }
        try {
            return Instant.parse(expiresAt);
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("无法解析 IAM 返回的过期时间 '{}', 使用默认有效期", expiresAt);
            return Instant.now().plusSeconds(obsProperties.getTokenDurationSeconds());
        }
    }

    public record TemporaryCredentials(
            String accessKey,
            String secretKey,
            String securityToken,
            Instant expiresAt) {
    }
}
