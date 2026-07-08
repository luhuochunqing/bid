package com.xiyu.bid.file.infrastructure.obs;

import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.auth.ICredential;
import com.huaweicloud.sdk.iam.v3.IamClient;
import com.huaweicloud.sdk.iam.v3.model.AssumeAgencyRequest;
import com.huaweicloud.sdk.iam.v3.model.AssumeAgencyRequestBody;
import com.huaweicloud.sdk.iam.v3.model.AssumeAgencyResponse;
import com.huaweicloud.sdk.iam.v3.model.Credentials;
import com.huaweicloud.sdk.iam.v3.region.IamRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

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

        ICredential auth = new BasicCredentials()
                .withAk(obsProperties.getAccessKey())
                .withSk(obsProperties.getSecretKey());

        try (IamClient client = IamClient.newBuilder()
                .withCredential(auth)
                .withRegion(IamRegion.valueOf(obsProperties.getRegion()))
                .build()) {

            AssumeAgencyRequestBody body = new AssumeAgencyRequestBody()
                    .withAgencyUrn(obsProperties.getAgencyUrn())
                    .withAgencySessionName("xiyu-bid-" + uploadId)
                    .withDurationSeconds(obsProperties.getTokenDurationSeconds().longValue());

            AssumeAgencyRequest request = new AssumeAgencyRequest().withBody(body);
            AssumeAgencyResponse response = client.assumeAgency(request);
            Credentials creds = response.getCredentials();

            if (creds == null) {
                throw new IllegalStateException("华为云 IAM 返回的临时凭证为空");
            }

            Instant expiresAt = parseExpiresAt(creds.getExpiresAt());

            return new TemporaryCredentials(
                    creds.getAccessKey(),
                    creds.getSecretKey(),
                    creds.getSecuritytoken(),
                    expiresAt);
        } catch (Exception e) {
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
        } catch (Exception e) {
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
