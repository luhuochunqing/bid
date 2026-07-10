package com.xiyu.bid.crm.application;

import com.xiyu.bid.dto.LoginRequest;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.RoleNotAuthorizedException;
import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * OSS 直接登录服务：封装本地无用户记录时的 OSS 实时鉴权 + 自动创建流程，
 * 以及 OSS 用户登录权限校验。
 * <p>
 * <b>根因背景</b>：AuthService.login() 本地查不到用户时原直接抛 UsernameNotFoundException，
 * 违反"OSS 实时鉴权是唯一真相源"设计意图。此服务封装修复逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssDirectLoginService {

    private final OssLoginFlowService ossLoginFlowService;
    private final OssUserAutoCreator ossUserAutoCreator;
    private final OssPermissionCache ossPermissionCache;

    /**
     * 尝试 OSS 直接登录：本地无用户记录时调用。
     * <p>
     * 流程：OSS 实时鉴权 → 成功则自动创建本地 User → 校验登录白名单 → 返回 Optional.of(user)；
     * 鉴权失败返回 Optional.empty()，由调用方决定抛异常。
     * <p>
     * 白名单校验在自动创建之后执行：先创建本地记录（满足外键约束），
     * 再检查 roleCode 是否在 LoginRoleWhitelist 允许范围内，
     * 不允许的角色抛 RoleNotAuthorizedException（fail-closed）。
     *
     * @param request 登录请求（含 username/password）
     * @return OSS 鉴权成功并自动创建的 User；鉴权失败返回 empty
     * @throws RoleNotAuthorizedException 如果 OSS 缓存角色不在登录白名单
     */
    public Optional<User> tryDirectLogin(LoginRequest request) {
        OssLoginResult ossResult = ossLoginFlowService.authenticateDirect(
                request.getUsername(), request.getPassword());
        if (!ossResult.isAuthenticated()) {
            log.warn("OSS direct login failed for user={}, no local record and OSS auth rejected",
                    request.getUsername());
            return Optional.empty();
        }
        User user = ossUserAutoCreator.autoCreateIfAbsent(ossResult);
        // 白名单校验：OSS 鉴权成功不等于允许登录，roleCode 必须在 LoginRoleWhitelist 范围内
        requireOssRole(user);
        log.info("OSS direct login succeeded, local user auto-created: username={}, userId={}",
                user.getUsername(), user.getId());
        return Optional.of(user);
    }

    /**
     * OSS 用户登录权限检查：必须有 OSS 缓存角色且为允许的业务角色。
     * 本地账号（非 OSS 同步，externalOrgSourceApp 为空）直接放行。
     *
     * @throws RoleNotAuthorizedException 当 OSS 缓存无角色或角色不在登录白名单
     */
    public void requireOssRole(User user) {
        if (!user.isOssUser()) {
            return;
        }
        Optional<String> cachedRoleCode = ossPermissionCache.getRoleCode(user.getUsername());
        if (cachedRoleCode.isEmpty() || cachedRoleCode.get().isBlank()) {
            log.warn("Login denied for user={}: no valid OSS role", user.getUsername());
            throw new RoleNotAuthorizedException("无有效 OSS 角色，不允许登录");
        }
        String roleCode = cachedRoleCode.get();
        if (!LoginRoleWhitelist.isAllowed(roleCode)) {
            log.warn("Login denied for user={}: role={} not in login whitelist", user.getUsername(), roleCode);
            throw new RoleNotAuthorizedException("角色未授权，不允许登录");
        }
        log.info("Login allowed for user={}: OSS role={}", user.getUsername(), roleCode);
    }
}
