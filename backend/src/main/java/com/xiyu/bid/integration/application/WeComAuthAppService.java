package com.xiyu.bid.integration.application;

import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Application service to handle WeCom authentication and user mapping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeComAuthAppService {

    /** Orchestrates OAuth2 flow with WeCom API. */
    private final WeComOAuthService weComOAuthService;

    /** Accesses local user accounts. */
    private final UserRepository userRepository;

    /** Handles login session creation. */
    private final AuthService authService;

    /** Accesses WeCom integration settings. */
    private final WeComIntegrationJpaRepository integrationRepository;

    /**
     * Attempts to login a user via WeCom OAuth2 code.
     * <p>
     * <b>已废弃</b>：此方法直接调用企业微信 API（需本地持有 secret），
     * 已被 {@link WeComSsoOssLoginService#loginByWeComCode} 取代（通过 base-oss 换 token，secret 由 OSS 持有）。
     * 保留仅作为对比路径与历史测试覆盖，新代码不应调用此方法。
     *
     * @param code WeCom OAuth2 code
     * @return AuthSessionResult if successful, empty if user needs binding
     * @deprecated 改用 {@link WeComSsoOssLoginService#loginByWeComCode(String)}
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public Optional<AuthSessionResult> loginByWeCom(final String code) {
        // 1. Get user info from WeCom
        return weComOAuthService.getAuthenticatedUserInfo(code)
                .flatMap(userInfo -> {
            String wecomUserId = userInfo.userId();
            if (wecomUserId == null) {
                log.warn("WeCom OAuth2 callback without UserId (OpenId={})",
                        userInfo.openId());
                return Optional.empty();
            }

            // 2. Try to find user by wecomUserId
            Optional<User> userOpt =
                    userRepository.findByWecomUserId(wecomUserId);

            if (userOpt.isEmpty() && userInfo.user_ticket() != null) {
                // 3. Try to find by mobile if wecomUserId is not found
                userOpt = weComOAuthService
                        .getUserDetail(userInfo.user_ticket())
                        .flatMap(detail -> {
                            String mobile = detail.mobile();
                            if (mobile != null && !mobile.isBlank()) {
                                return userRepository.findByPhone(mobile);
                            }
                            return Optional.empty();
                        })
                        .map(user -> {
                            // Link wecomUserId to the found user
                            user.setWecomUserId(wecomUserId);
                            return userRepository.save(user);
                        });
            }

            // 4. Return login result if user found
            return userOpt.map(authService::loginWithoutPassword);
        });
    }

    /**
     * Gets the authorization parameters for the frontend to construct the URL.
     * <p>
     * 同时校验 ssoEnabled，未启用时只返回 state（前端拿到空 appid/agentid 会显示
     * "企业微信集成配置不完整"），避免用户跳转企微授权完成后回调被拒的割裂体验。
     *
     * @param state CSRF state token
     * @return Map containing appid, agentid and state; 若 SSO 未启用则只含 state
     */
    public Map<String, String> getAuthorizeParams(final String state) {
        var entityOpt = integrationRepository.findById(1L)
                .filter(WeComIntegrationEntity::isSsoEnabled);
        if (entityOpt.isEmpty()) {
            log.warn("WeCom authorize-params: integration not configured (ID=1) or SSO disabled");
            return Map.of("state", state);
        }
        var entity = entityOpt.get();
        return Map.of(
                "appid", entity.getCorpId(),
                "agentid", entity.getAgentId(),
                "state", state
        );
    }
}
