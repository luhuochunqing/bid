package com.xiyu.bid.security;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 当前用户解析器。
 * <p>统一封装「从 SecurityContext 获取认证信息 → 查询 DB 获取 User 实体」的逻辑，
 * 并利用 @RequestScope 做请求级缓存，避免同一次请求内多次查询。</p>
 * <p>
 * <b>注意</b>：新代码优先在 Controller 层通过 @AuthenticationPrincipal 获取用户身份再传入 Service，
 * 而非在 Service 层直接调用此 Resolver。此 Resolver 主要用于遗留代码过渡和 AOP/切面等无法从参数获取用户的场景。
 * </p>
 */
@Component
@RequestScope
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    private User cachedUser;
    private boolean resolved;

    /**
     * 获取当前认证用户。
     *
     * @return 当前用户，未认证或不存在时返回 null
     */
    public User getCurrentUser() {
        if (resolved) {
            return cachedUser;
        }
        resolved = true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            cachedUser = null;
            return null;
        }
        cachedUser = userRepository.findByUsername(auth.getName()).orElse(null);
        return cachedUser;
    }

    /**
     * 获取当前用户 ID。
     *
     * @return 当前用户 ID，未认证或不存在时返回 null
     */
    public Long getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户角色 code。
     *
     * @return 当前用户角色 code，未认证或不存在时返回 null
     */
    public String getCurrentRoleCode() {
        User user = getCurrentUser();
        return user != null ? user.getRoleCode() : null;
    }

    /**
     * 要求当前用户必须已认证且存在，否则抛异常。
     *
     * @return 当前用户
     * @throws org.springframework.security.access.AccessDeniedException 未认证或用户不存在时
     */
    public User requireCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("无法识别当前用户");
        }
        return user;
    }
}
