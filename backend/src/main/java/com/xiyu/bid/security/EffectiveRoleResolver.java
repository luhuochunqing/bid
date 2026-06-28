package com.xiyu.bid.security;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.security.domain.EffectiveRolePolicy;
import com.xiyu.bid.security.domain.EffectiveRoleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 有效角色码解析器（外壳编排）。
 *
 * <p>服务层权限校验的<b>唯一</b>角色码读取入口。读取 OSS 权限缓存后委托纯核心
 * {@link EffectiveRolePolicy#decide} 决策，按决策来源分级记录日志。
 *
 * <p>取代此前各 Guard/Service 直调 {@link User#getRoleCode()} 的分歧路径。
 * 根因（CO-373）：OSS 用户 {@code role_id=NULL} 时实体回退返回 "manager"，
 * 导致权限校验误拒。本 resolver 确保 OSS 用户以缓存角色码为准，缓存未命中时
 * fail-closed 返回 null，绝不回退 "manager" 放行。
 *
 * <p>职责边界（FP-Java Profile Split-First）：
 * <ul>
 *   <li>本类（外壳）：读缓存 I/O、取实体属性、调纯核心、记日志（副作用）</li>
 *   <li>{@link EffectiveRolePolicy}（纯核心）：决策逻辑（无副作用，可单测）</li>
 *   <li>{@link RoleCodeCachePort}（端口）：缓存读取抽象，由 crm 包适配器实现</li>
 * </ul>
 *
 * <p>依赖方向：本类仅依赖 {@link RoleCodeCachePort} 接口（位于 security 包），
 * 不直接依赖 {@code com.xiyu.bid.crm.application.OssPermissionCache}，以打破
 * security ↔ crm 循环依赖（crm 的登录流程已反向依赖 security 的 LoginRoleWhitelist）。
 */
@Component
public class EffectiveRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(EffectiveRoleResolver.class);

    private final RoleCodeCachePort roleCodeCachePort;

    public EffectiveRoleResolver(RoleCodeCachePort roleCodeCachePort) {
        this.roleCodeCachePort = roleCodeCachePort;
    }

    /**
     * 解析用户的角色码（含决策来源，用于日志/测试断言）。
     *
     * @param user 当前用户，可为 null（返回 null）
     * @return 解析结果；user 为 null 时返回 {@code new EffectiveRoleResult(null, null)}
     */
    public EffectiveRoleResult resolve(User user) {
        if (user == null) {
            return new EffectiveRoleResult(null, null);
        }
        String username = user.getUsername();
        java.util.Optional<String> cachedRoleCode = roleCodeCachePort.getRoleCode(username);
        String entityRoleCode = user.getRoleCode();
        boolean isOssUser = isOssUser(user);
        EffectiveRoleResult result = EffectiveRolePolicy.decide(cachedRoleCode, entityRoleCode, isOssUser);
        logDecision(user, result);
        return result;
    }

    /**
     * 解析用户的角色码（便捷方法，仅返回角色码字符串）。
     *
     * <p>OSS 用户缓存未命中时返回 {@code null}（fail-closed），调用方应据此拒绝敏感操作。
     *
     * @param user 当前用户，可为 null
     * @return 有效角色码；user 为 null 或 fail-closed 时为 null
     */
    public String resolveRoleCode(User user) {
        return resolve(user).roleCode();
    }

    /**
     * 判断是否 OSS 同步用户：external_org_source_app 非空非 blank。
     * 与 PR #1241 {@code ProjectDraftingService.resolveEffectiveRoleCode} 判定逻辑一致。
     */
    private boolean isOssUser(User user) {
        String sourceApp = user.getExternalOrgSourceApp();
        return sourceApp != null && !sourceApp.isBlank();
    }

    /**
     * 按决策来源分级记录日志（FR-009 可观测性）。
     * CACHE_HIT / LOCAL_USER → debug；CACHE_MISS_FAIL_CLOSED → warn。
     */
    private void logDecision(User user, EffectiveRoleResult result) {
        if (result.source() == null) {
            return;
        }
        switch (result.source()) {
            case CACHE_HIT -> log.debug("Effective role resolved: user={}, roleCode={}, source=CACHE_HIT",
                user.getUsername(), result.roleCode());
            case LOCAL_USER -> log.debug("Effective role resolved: user={}, roleCode={}, source=LOCAL_USER",
                user.getUsername(), result.roleCode());
            case CACHE_MISS_FAIL_CLOSED -> log.warn(
                "OSS user={} role cache miss, fail-closed (roleCode=null). "
                + "Permission checks will deny sensitive operations until re-login.",
                user.getUsername());
            default -> { /* no-op */ }
        }
    }
}
