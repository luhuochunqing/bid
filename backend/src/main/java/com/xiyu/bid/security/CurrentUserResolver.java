package com.xiyu.bid.security;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 当前用户解析器。
 * <p>统一封装「从 SecurityContext 获取认证信息 → 查询 DB 获取 User 实体」的逻辑。</p>
 * <p>
 * <b>注意</b>：新代码优先在 Controller 层通过 @AuthenticationPrincipal 获取用户身份再传入 Service，
 * 而非在 Service 层直接调用此 Resolver。此 Resolver 主要用于遗留代码过渡和 AOP/切面等无法从参数获取用户的场景。
 * </p>
 * <p>
 * <b>设计决策</b>：不使用 @RequestScope 进行请求级缓存，原因见 Lesson #13：
 * ehsy 组织事件 SDK 在 ApplicationReadyEvent 时通过 getBeansOfType() 扫描所有 bean，
 * 若存在 @RequestScope bean 会导致 ScopeNotActiveException 启动失败。
 * 改为单例 + 每次直接查询数据库（走主键/用户名索引，性能可忽略）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    /**
     * 获取当前认证用户。
     * <p>每次调用直接查询数据库，不使用请求级缓存。
     * 用户查询走 username 索引，性能影响可忽略。</p>
     *
     * @return 当前用户，未认证或不存在时返回 null
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
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
     * <p>未认证时抛 {@link AuthenticationCredentialsNotFoundException}，
     * 认证通过但用户在 DB 中不存在时抛 {@link AccessDeniedException}。</p>
     *
     * @return 当前用户
     * @throws AuthenticationCredentialsNotFoundException 未认证时
     * @throws AccessDeniedException 用户不存在时
     */
    public User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("当前用户未认证");
        }
        User user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        return user;
    }
}
