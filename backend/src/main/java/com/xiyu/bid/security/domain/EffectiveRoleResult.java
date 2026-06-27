package com.xiyu.bid.security.domain;

/**
 * 有效角色码解析结果。
 *
 * <p>由 {@link EffectiveRolePolicy#decide} 产出，包含角色码与决策来源。
 * 外壳层 {@code EffectiveRoleResolver} 据此记录解析决策日志（FR-009）。
 *
 * <p>不可变 record，符合 FP-Java Profile。
 *
 * @param roleCode 有效角色码；fail-closed 时为 {@code null}
 * @param source    决策来源
 */
public record EffectiveRoleResult(
    String roleCode,
    Source source
) {
    /**
     * 角色码解析决策来源。
     */
    public enum Source {
        /** OSS 权限缓存命中，返回缓存角色码 */
        CACHE_HIT,
        /** 非 OSS 用户，返回实体角色码（role_id 非空，回退合法） */
        LOCAL_USER,
        /** OSS 用户但缓存未命中，fail-closed 返回 null（不回退 manager） */
        CACHE_MISS_FAIL_CLOSED
    }
}
