package com.xiyu.bid.crm.application;

import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Home 平台 SSO 登录服务。
 * <p>
 * 按接口文档 oss-integration-api.md §单点登录（SSO）实现方案：
 * 接收 Home 平台跳转携带的 token，调用 OSS 实时接口校验 token 并获取用户信息、权限、角色，
 * 写入内存缓存，不依赖本地 DB 判断用户有效性。
 * <p>
 * 本地 User 仅用于生成 JWT 和 RefreshSession（外键关联），不检查 enabled 字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeSsoService {

    private final OssLoginFlowService ossLoginFlowService;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public AuthSessionResult ssoLogin(String token) {
        // 调用 OSS 实时接口：getUserInfo → getUserPermission → getUserJobList → 写缓存
        OssLoginResult loginResult = ossLoginFlowService.authenticateWithExistingToken(token);

        if (!loginResult.isAuthenticated()) {
            log.warn("Home SSO login failed: OSS token validation failed");
            throw new BadCredentialsException("SSO token 无效或已过期");
        }

        String username = loginResult.username();
        if (username == null || username.isBlank()) {
            log.warn("Home SSO login failed: OSS returned empty username");
            throw new BadCredentialsException("SSO 登录失败：无法获取用户信息");
        }

        // 本地 User 仅用于生成 JWT 和 RefreshSession，不检查 enabled（用户有效性由 OSS 实时判断）
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Home SSO login failed: local user record not found, username={}", username);
                    return new BadCredentialsException("本地无此用户记录，请联系管理员创建账号");
                });

        log.info("Home SSO login success: username={}, nickName={}",
                username, loginResult.employeeInfo() != null
                        ? loginResult.employeeInfo().path("nickName").asText("") : "");
        return authService.loginWithoutPassword(user);
    }
}
