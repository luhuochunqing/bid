package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.domain.CrmToken;
import com.xiyu.bid.crm.domain.CrmTokenCache;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.time.Instant;

@Service
public class CrmAuthService {

    private static final Logger log = LoggerFactory.getLogger(CrmAuthService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final CrmTokenCache tokenCache;

    private volatile int consecutiveFailures = 0;
    private volatile Instant coolDownUntil = null;

    public CrmAuthService(CrmHttpClient httpClient, CrmProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.tokenCache = new CrmTokenCache();
    }

    public String getValidToken() {
        return tokenCache.getOrFetch(this::applyToken, properties.getTokenRenewBeforeExpiryRatio())
                .accessToken();
    }

    public void logout() {
        CrmToken token = tokenCache.get().orElse(null);
        if (token != null) {
            try {
                String baseUrl = properties.getEffectiveAuthBaseUrl();
                String path = properties.getAuth().getLogoutPath();
                httpClient.post(baseUrl, path, token.accessToken(), null);
            } catch (RuntimeException e) {
                log.warn("CRM logout request failed (non-fatal): {}", e.getMessage());
            }
        }
        tokenCache.clear();
        log.info("CRM token cache cleared (logout)");
    }

    public void handleUnauthorized() {
        tokenCache.clear();
        log.info("CRM token cache cleared due to 401, will re-apply on next request");
    }

    private CrmToken applyToken() {
        if (coolDownUntil != null && Instant.now().isBefore(coolDownUntil)) {
            throw new IllegalStateException("CRM token apply in cooldown after " + consecutiveFailures + " consecutive failures");
        }

        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getOauthLoginPath();
        org.springframework.util.LinkedMultiValueMap<String, String> formData = new org.springframework.util.LinkedMultiValueMap<>();
        formData.add("username", properties.getOauthUsername());
        formData.add("password", properties.getOauthPassword());
        formData.add("system", properties.getOauthSystem());
        log.info("CRM oauth login: baseUrl={}, path={}, username={}", baseUrl, path, properties.getOauthUsername());
        CrmResponseHandler.CrmApiResponse response = httpClient.postForm(baseUrl, path, formData);

        if (response.data() != null && response.data().has("access_token")) {
            String accessToken = response.data().path("access_token").asText();
            long expiresIn = response.data().path("expires_in").asLong(5998);
            consecutiveFailures = 0;
            coolDownUntil = null;
            CrmToken token = new CrmToken(accessToken, expiresIn, Instant.now());
            log.info("CRM token acquired: {}", token);
            return token;
        }
        if (response.success() && response.data() != null) {
            String accessToken = response.data().path("access_token").asText();
            long expiresIn = response.data().path("expires_in").asLong(5998);
            consecutiveFailures = 0;
            coolDownUntil = null;
            CrmToken token = new CrmToken(accessToken, expiresIn, Instant.now());
            log.info("CRM token acquired: {}", token);
            return token;
        }

        consecutiveFailures++;
        if (consecutiveFailures >= properties.getTokenCoolDownRetries()) {
            coolDownUntil = Instant.now().plusMillis(properties.getTokenCoolDownMs());
            log.error("CRM token apply failed {} times consecutively, entering cooldown until {}", consecutiveFailures, coolDownUntil);
        }
        throw new IllegalStateException("CRM applyToken failed: code=" + response.code() + " msg=" + response.msg());
    }
}
