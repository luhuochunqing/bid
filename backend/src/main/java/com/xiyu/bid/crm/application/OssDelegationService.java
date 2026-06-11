package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

/**
 * 西域 OSS 统一认证委托服务。
 * 组织架构同步用户登录时，将凭证转发到西域 OSS /oauth/login 验证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssDelegationService {

    private final CrmHttpClient crmHttpClient;
    private final CrmProperties crmProperties;

    /**
     * 委托西域 OSS 认证用户密码。
     * @param user 组织架构同步的用户（有 externalOrgSourceApp）
     * @param rawPassword 用户输入的明文密码
     * @return true 如果 OSS 验证通过
     */
    public boolean authenticate(User user, String rawPassword) {
        String baseUrl = crmProperties.getEffectiveAuthBaseUrl();
        String path = crmProperties.getAuth().getOauthLoginPath();

        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", user.getUsername());
        formData.add("password", rawPassword);
        formData.add("system", crmProperties.getOauthSystem());

        log.info("OSS login delegation: baseUrl={}, path={}, username={}, system={}",
                baseUrl, path, user.getUsername(), crmProperties.getOauthSystem());

        CrmResponseHandler.CrmApiResponse response = crmHttpClient.postForm(baseUrl, path, formData);

        if (response.success()) {
            log.info("OSS login succeeded for user: {}", user.getUsername());
            return true;
        }

        log.warn("OSS login failed for user: {} code={} msg={}",
                user.getUsername(), response.code(), response.msg());
        return false;
    }
}
