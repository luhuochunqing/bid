package com.xiyu.bid.admin.service;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import com.xiyu.bid.security.domain.EffectiveRoleResult;

import java.util.List;
import java.util.Optional;

/**
 * 解析用户对应的 RoleProfile（§78 修复 5：双数据源根治）。
 * <p>
 * 单一职责：roleCode（来自 {@link EffectiveRoleResolver}，缓存优先）→ RoleProfile 的 DB 查询 + catalog fallback。
 * <p>
 * 从 DataScopeConfigService 拆出，避免主应用服务超过 300 行（ResponsibilityArchitectureTest）。
 * <p>
 * 关键不变量：
 * <ul>
 *   <li>roleCode 统一走 {@link EffectiveRoleResolver}，不直读 user.getRoleCode()——
 *       OSS 同步流程不更新 DB roleProfile（OssLoginFlowService 日志 "not written to DB"），
 *       覃超颖 DB role_id=20 (bid-otherDept) 但 OSS 实际配置 /bidAdmin——直读 DB 会拿错角色码计算 dataScope，导致 403。</li>
 *   <li>fail-closed 路径（OSS_ADMIN_REJECTED / CACHE_MISS_FAIL_CLOSED，roleCode=null）显式走 unregistered
 *       而非 catalog fallback——definitionForCode(null) 会返回 ADMIN_CODE 定义 dataScope=all，
 *       会导致 OSS 用户越权拿到 admin 数据范围。</li>
 *   <li>未注册 roleCode 不 fallback 到 staff——避免前端 AuthResponse.menuPermissions 越权可见
 *       标讯/项目/知识库菜单。后端 API 已由 UserDetailsServiceImpl 的 shouldSkipLegacyRoleCompat 挡住（403），
 *       此处收紧前端可见性，消除"看到菜单却点不进"的不一致。</li>
 * </ul>
 *
 * @see EffectiveRoleResolver
 * @see RoleProfileCatalog
 */
final class DataScopeRoleProfileResolver {

    private final RoleProfileRepository roleProfileRepository;
    private final EffectiveRoleResolver effectiveRoleResolver;

    DataScopeRoleProfileResolver(
            RoleProfileRepository pRoleProfileRepository,
            EffectiveRoleResolver pEffectiveRoleResolver
    ) {
        this.roleProfileRepository = pRoleProfileRepository;
        this.effectiveRoleResolver = pEffectiveRoleResolver;
    }

    /**
     * 解析用户的 RoleProfile：roleCode（缓存优先）→ DB 查询 → catalog fallback / unregistered。
     */
    RoleProfile resolve(User user) {
        if (user == null) {
            return unregisteredPlaceholder(null);
        }
        EffectiveRoleResult result = effectiveRoleResolver.resolve(user);
        if (result.roleCode() == null) {
            return unregisteredPlaceholder(null);
        }
        String roleCode = result.roleCode();
        Optional<RoleProfile> roleProfile = roleProfileRepository.findByCodeIgnoreCase(roleCode);
        if (roleProfile.isPresent()) {
            return roleProfile.get();
        }
        if (roleCode != null && !roleCode.isBlank() && !RoleProfileCatalog.isRegisteredCode(roleCode)) {
            return unregisteredPlaceholder(roleCode);
        }
        RoleProfileCatalog.SeedDefinition definition = RoleProfileCatalog.definitionForCode(roleCode);
        RoleProfile fallbackRole = RoleProfile.builder()
                .code(definition.code())
                .name(definition.name())
                .description(definition.description())
                .isSystem(definition.system())
                .enabled(true)
                .dataScope(definition.dataScope())
                .build();
        fallbackRole.setMenuPermissions(definition.menuPermissions());
        return fallbackRole;
    }

    private RoleProfile unregisteredPlaceholder(String roleCode) {
        RoleProfile placeholder = RoleProfile.builder()
                .code(roleCode)
                .name(roleCode)
                .isSystem(false)
                .enabled(true)
                .dataScope("self")
                .build();
        placeholder.setMenuPermissions(List.of());
        return placeholder;
    }
}
