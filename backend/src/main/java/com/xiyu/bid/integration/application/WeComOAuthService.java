package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComApiErrCode;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application service for WeCom OAuth2 orchestration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeComOAuthService {

    private final WeComApiClient apiClient;
    private final WeComAccessTokenProvider tokenProvider;
    private final WeComIntegrationJpaRepository integrationRepository;
    private final WeComCredentialCipher cipher;

    /**
     * Orchestrates the OAuth2 callback flow to retrieve user info.
     *
     * @param code The OAuth2 code from WeCom
     * @return User info response from WeCom
     */
    public Optional<WeComApiClient.WeComUserInfoResponse> getAuthenticatedUserInfo(String code) {
        // 1. Load active WeCom integration config
        // In this project, we assume a single active config for simplicity, or we could pass an ID
        return integrationRepository.findAll().stream()
                .filter(WeComIntegrationEntity::isSsoEnabled)
                .findFirst()
                .flatMap(config -> {
                    try {
                        // 2. Decrypt secret
                        String plainSecret = cipher.decrypt(config.getEncryptedSecret());

                        // 3. Get access token
                        String token = tokenProvider.getAccessToken(
                                config.getCorpId(),
                                config.getAgentId(),
                                plainSecret
                        );

                        // 4. Get user info
                        var response = apiClient.requestUserInfo(token, code);
                        
                        if (response.errcode() == WeComApiErrCode.OK.code()) {
                            return Optional.of(response);
                        } else {
                            log.warn("WeCom getuserinfo returned error: {} - {}", response.errcode(), response.errmsg());
                            return Optional.empty();
                        }
                    } catch (Exception e) {
                        log.error("Failed to authenticate WeCom user with code: {}", code, e);
                        return Optional.empty();
                    }
                });
    }

    /**
     * Fetches detailed user info if a user_ticket is available.
     */
    public Optional<WeComApiClient.WeComUserDetailResponse> getUserDetail(String userTicket) {
        return integrationRepository.findAll().stream()
                .filter(WeComIntegrationEntity::isSsoEnabled)
                .findFirst()
                .flatMap(config -> {
                    try {
                        String plainSecret = cipher.decrypt(config.getEncryptedSecret());
                        String token = tokenProvider.getAccessToken(
                                config.getCorpId(),
                                config.getAgentId(),
                                plainSecret
                        );
                        var response = apiClient.requestUserDetail(token, userTicket);
                        if (response.errcode() == WeComApiErrCode.OK.code()) {
                            return Optional.of(response);
                        }
                        return Optional.empty();
                    } catch (Exception e) {
                        log.error("Failed to get WeCom user detail", e);
                        return Optional.empty();
                    }
                });
    }
}
