package com.xiyu.bid.security.domain;

import java.util.Optional;

/**
 * 有效角色码解析决策（纯核心）。
 *
 * <p>根据 OSS 权限缓存值、用户实体角色码与 OSS 用户标识，决定服务层权限校验应使用的角色码。
 * 不依赖 Spring/Redis/JPA，可独立单元测试。缓存读取等副作用由外壳层
 * {@code EffectiveRoleResolver} 完成后将值传入。
 *
 * <p>解析优先级（FR-002）：
 * <ol>
 *   <li>OSS 缓存命中（非空非 blank）→ {@link EffectiveRoleResult.Source#CACHE_HIT} + 缓存值</li>
 *   <li>非 OSS 用户 → {@link EffectiveRoleResult.Source#LOCAL_USER} + 实体角色码</li>
 *   <li>OSS 用户缓存未命中 → {@link EffectiveRoleResult.Source#CACHE_MISS_FAIL_CLOSED} + null（不回退 "manager"）</li>
 * </ol>
 *
 * <p>根因背景（CO-373）：OSS 同步用户 {@code role_id=NULL}，实体回退方法返回硬编码 "manager"，
 * 导致服务层权限校验误拒（不在角色白名单内）。本纯核心确保 OSS 用户始终以缓存角色码为准，
 * 缓存未命中时 fail-closed，绝不回退 "manager" 放行。
 *
 * <p>纯核心（Pure Core）：不可变、无框架依赖、可独立单元测试。
 */
public final class EffectiveRolePolicy {

    private EffectiveRolePolicy() {
    }

    /**
     * 决定有效角色码。
     *
     * @param cachedRoleCode OSS 权限缓存角色码（命中时非空，未命中为 empty；空字符串视为未命中）
     * @param entityRoleCode 用户实体的角色码（{@code roleProfile.code} 或实体回退值；可空）
     * @param isOssUser      是否 OSS 同步用户（{@code external_org_source_app} 非空）
     * @return 解析结果，含角色码与决策来源
     */
    public static EffectiveRoleResult decide(
        Optional<String> cachedRoleCode,
        String entityRoleCode,
        boolean isOssUser
    ) {
        if (cachedRoleCode != null && cachedRoleCode.isPresent()) {
            String cached = cachedRoleCode.get();
            if (cached != null && !cached.isBlank()) {
                return new EffectiveRoleResult(cached.trim(), EffectiveRoleResult.Source.CACHE_HIT);
            }
        }
        if (!isOssUser) {
            return new EffectiveRoleResult(entityRoleCode, EffectiveRoleResult.Source.LOCAL_USER);
        }
        return new EffectiveRoleResult(null, EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED);
    }
}
