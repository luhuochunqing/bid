// Input: Sentry SDK 自动配置 + Spring Security 用户上下文
// Output: 异常自动聚合到 Sentry，附带用户/环境/版本上下文
// Pos: Config/基础设施层 — 错误诊断系统
package com.xiyu.bid.config;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.security.EffectiveRoleResolver;
import io.sentry.SentryOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * Sentry 错误追踪配置。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>注入用户上下文（userId、roleCode）到 Sentry 事件，实现"谁触发了这个错误"</li>
 *   <li>过滤非系统缺陷（AccessDeniedException、AuthenticationException 等正常业务拒绝）</li>
 *   <li>自动读取 git.properties 作为 release 版本标识</li>
 * </ul>
 *
 * <p>无 DSN 时 Sentry SDK 自动降级为 no-op，不影响正常业务。</p>
 */
@Configuration
@ConditionalOnProperty(name = "sentry.dsn", matchIfMissing = false)
public class SentryConfig {

    private static final Logger log = LoggerFactory.getLogger(SentryConfig.class);

    private static final Set<String> NON_CRITICAL_EXCEPTIONS = Set.of(
            AccessDeniedException.class.getName(),
            AuthenticationException.class.getName(),
            "com.xiyu.bid.exception.BusinessException",
            "com.xiyu.bid.exception.ResourceNotFoundException",
            "com.xiyu.bid.exception.AppFailureException",
            "com.xiyu.bid.exception.RoleNotAuthorizedException",
            "com.xiyu.bid.exception.TenderDuplicateException"
    );

    private final CurrentUserResolver currentUserResolver;
    private final EffectiveRoleResolver effectiveRoleResolver;

    @Value("${sentry.release:#{null}}")
    private String sentryRelease;

    /** 缓存 git commit hash，运行时不变，避免每次事件读文件 */
    private String cachedRelease;

    public SentryConfig(CurrentUserResolver currentUserResolver,
                         EffectiveRoleResolver effectiveRoleResolver) {
        this.currentUserResolver = currentUserResolver;
        this.effectiveRoleResolver = effectiveRoleResolver;
    }

    @PostConstruct
    void init() {
        this.cachedRelease = resolveRelease();
        if (cachedRelease != null) {
            log.info("Sentry release resolved: {}", cachedRelease);
        }
    }

    /**
     * beforeSend 回调：过滤非关键异常 + 注入用户上下文 + 注入 release 版本。
     *
     * <p>过滤规则：以下异常是正常的业务逻辑拒绝，不是系统缺陷，不上报 Sentry：</p>
     * <ul>
     *   <li>AccessDeniedException — 用户无权限（403），正常权限控制</li>
     *   <li>AuthenticationException — 认证失败（401），正常登录流程</li>
     *   <li>BusinessException — 业务规则拒绝（400），正常业务校验</li>
     *   <li>ResourceNotFoundException — 资源不存在（404），正常查询结果</li>
     * </ul>
     *
     * <p>真正的系统缺陷（NPE、SQL 异常、外部服务调用失败等）会完整上报。</p>
     */
    @Bean
    public SentryOptions.BeforeSendCallback beforeSendCallback() {
        return (event, hint) -> {
            // 1. 过滤非关键异常
            Throwable throwable = event.getThrowable();
            if (throwable != null) {
                String className = throwable.getClass().getName();
                if (NON_CRITICAL_EXCEPTIONS.contains(className)) {
                    return null; // 丢弃事件，不上报
                }
            }

            // 2. 注入用户上下文
            injectUserContext(event);

            // 3. 注入 release 版本
            injectRelease(event);

            return event;
        };
    }

    private void injectUserContext(io.sentry.SentryEvent event) {
        try {
            User currentUser = currentUserResolver.getCurrentUser();
            if (currentUser != null) {
                io.sentry.protocol.User sentryUser = new io.sentry.protocol.User();
                sentryUser.setId(String.valueOf(currentUser.getId()));
                sentryUser.setUsername(currentUser.getUsername());
                String roleCode = effectiveRoleResolver.resolveRoleCode(currentUser);
                sentryUser.setOthers(java.util.Map.of(
                        "roleCode", roleCode != null ? roleCode : "unknown",
                        "fullName", currentUser.getFullName() != null ? currentUser.getFullName() : ""
                ));
                event.setUser(sentryUser);
            }
        } catch (RuntimeException e) {
            // 用户上下文注入失败不影响事件上报
            log.debug("Failed to inject user context into Sentry event: {}", e.getMessage());
        }
    }

    private void injectRelease(io.sentry.SentryEvent event) {
        if (event.getRelease() == null || event.getRelease().isBlank()) {
            if (cachedRelease != null && !cachedRelease.isBlank()) {
                event.setRelease(cachedRelease);
            }
        }
    }

    private String resolveRelease() {
        // 优先使用显式配置
        if (sentryRelease != null && !sentryRelease.isBlank()) {
            return sentryRelease;
        }
        // 从 git.properties 自动读取
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String commitId = props.getProperty("git.commit.id.full");
                if (commitId != null && !commitId.isBlank()) {
                    return commitId.substring(0, Math.min(commitId.length(), 7));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read git.properties for Sentry release: {}", e.getMessage());
        }
        return null;
    }
}