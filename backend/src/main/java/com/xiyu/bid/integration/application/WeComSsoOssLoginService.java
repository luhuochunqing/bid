package com.xiyu.bid.integration.application;

import com.xiyu.bid.crm.application.OssLoginFlowService;
import com.xiyu.bid.crm.application.OssLoginResult;
import com.xiyu.bid.crm.application.OssUserAutoCreator;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 企微 SSO 登录服务（via base-oss）。
 * <p>
 * 流程：
 * <ol>
 *   <li>从 WeComIntegrationEntity 取 agentId（ssoEnabled=true 才放行）</li>
 *   <li>GET base-oss /qyWeixin/loginQywx?code=xxx&agentId=xxx → 拿到 OSS access_token</li>
 *   <li>用 OSS token 走 {@link OssLoginFlowService#authenticateWithExistingToken} 完整流程</li>
 *   <li>本地无 User 时由 {@link OssUserAutoCreator#autoCreateIfAbsent} 自动创建</li>
 *   <li>调用 {@link AuthService#loginWithoutPassword} 生成 JWT/RefreshSession</li>
 * </ol>
 * <p>
 * 与旧 {@link WeComAuthAppService#loginByWeCom} 的区别：本服务通过 base-oss 换 token，
 * secret 由 OSS 持有；旧服务直接调企业微信 API（需本地持有 secret）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComSsoOssLoginService {

    private final CrmHttpClient crmHttpClient;
    private final CrmProperties crmProperties;
    private final OssLoginFlowService ossLoginFlowService;
    private final OssUserAutoCreator ossUserAutoCreator;
    private final AuthService authService;
    private final WeComIntegrationJpaRepository integrationRepository;

    /**
     * 通过企微 OAuth code 走 base-oss 完成 SSO 登录。
     *
     * @param code 企微 OAuth2 code（前端从 URL 参数获取）
     * @return 登录结果；集成未配置 / SSO 未启用 / OSS 换 token 失败 / OSS 鉴权失败 时返回 empty
     */
    @Transactional
    public Optional<AuthSessionResult> loginByWeComCode(final String code) {
        if (code == null || code.isBlank()) {
            log.warn("WeCom SSO login failed: empty code");
            return Optional.empty();
        }

        // 1. 取 WeCom 集成配置（ID=1 单行配置表，与 WeComAuthAppService 对称）
        // 同时校验 ssoEnabled，避免前端拿到 appid/agentid 跳转后回调被拒
        Optional<WeComIntegrationEntity> integrationOpt = integrationRepository.findById(1L)
                .filter(WeComIntegrationEntity::isSsoEnabled);
        if (integrationOpt.isEmpty()) {
            log.warn("WeCom SSO login failed: integration not configured (ID=1) or SSO disabled");
            return Optional.empty();
        }
        WeComIntegrationEntity integration = integrationOpt.get();
        String agentId = integration.getAgentId();
        if (agentId == null || agentId.isBlank()) {
            log.warn("WeCom SSO login failed: agentId is empty in integration config");
            return Optional.empty();
        }

        // 2. 调用 base-oss /qyWeixin/loginQywx 换 OSS token
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String qywxLoginPath = crmProperties.getAuth().getQywxLoginPath();
        log.info("WeCom SSO: exchanging code for OSS token via {}/{}", baseUrl, qywxLoginPath);

        // CrmHttpClient.getQywxLogin 永不返回 null（异常时返回 parseError），无需 null 判断
        CrmResponseHandler.CrmApiResponse response =
                crmHttpClient.getQywxLogin(baseUrl, qywxLoginPath, code, agentId);
        // [TEMP-DEBUG] 联调期间临时打印 base-oss 完整响应，验证返回结构假设。联调完成后删除。
        log.info("WeCom SSO [TEMP-DEBUG]: base-oss response code={} success={} data={}",
                response.code(), response.success(), response.data());
        if (!response.success() || response.data() == null) {
            log.warn("WeCom SSO: base-oss loginQywx failed, code={} msg={}",
                    response.code(), response.msg());
            return Optional.empty();
        }

        String ossToken = response.data().path("access_token").asText(null);
        if (ossToken == null || ossToken.isBlank()) {
            log.warn("WeCom SSO: base-oss returned empty access_token, data={}", response.data());
            return Optional.empty();
        }

        // 3. 用 OSS token 走完整 OSS 登录流程（getUserInfo → getUserPermission → getUserJobList → 写缓存）
        OssLoginResult ossResult = ossLoginFlowService.authenticateWithExistingToken(ossToken);
        if (!ossResult.isAuthenticated()) {
            log.warn("WeCom SSO: OSS authenticateWithExistingToken returned authenticated=false");
            return Optional.empty();
        }

        // 4. OSS 鉴权已成功，本地无记录时自动创建（与 HomeSsoService 对称）
        User user = ossUserAutoCreator.autoCreateIfAbsent(ossResult);

        log.info("WeCom SSO login success: username={}", ossResult.username());
        return Optional.of(authService.loginWithoutPassword(user));
    }
}
