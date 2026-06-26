package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.HomeSsoClient;
import com.xiyu.bid.dto.AuthSessionResult;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeSsoService {

    private final HomeSsoClient homeSsoClient;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional
    public AuthSessionResult ssoLogin(String token) {
        String username = homeSsoClient.validateTokenAndGetUsername(token)
                .orElseThrow(() -> {
                    log.warn("Home SSO login failed: invalid or expired token");
                    return new BadCredentialsException("SSO token 无效或已过期");
                });

        User user = userRepository.findByUsername(username)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElseThrow(() -> {
                    log.warn("Home SSO login failed: user not found or disabled, username={}", username);
                    return new BadCredentialsException("用户不存在或已禁用，请联系管理员");
                });

        log.info("Home SSO login success: username={}", username);
        return authService.loginWithoutPassword(user);
    }
}
