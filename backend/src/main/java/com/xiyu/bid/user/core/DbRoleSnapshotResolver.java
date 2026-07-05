// Input: User 实体
// Output: 该用户当前在 DB 中的角色码（role_profile.code 快照）
// Pos: user/core/ - 纯核心解析
// 维护声明: 仅封装 User.getRoleCode()，统一“选人后操作”场景的角色快照读取口径。

package com.xiyu.bid.user.core;

import com.xiyu.bid.entity.User;

/**
 * 数据库角色快照解析器。
 * <p>
 * 用于“选人后立即操作该用户”的业务场景（如项目转移、任务分配）。
 * 这些场景下，前端 UserPicker 展示的角色来自事件库同步到 DB 的 {@code role_profile}，
 * 后端校验必须与该数据源对齐，而不是 OSS 登录缓存。
 * </p>
 * <p>
 * 与 {@link com.xiyu.bid.security.EffectiveRoleResolver} 的区别：
 * <ul>
 *   <li>{@code EffectiveRoleResolver}：登录鉴权时使用，OSS 用户优先读 OSS 缓存。</li>
 *   <li>{@code DbRoleSnapshotResolver}：业务操作时使用，统一读 DB {@code role_profile} 快照。</li>
 * </ul>
 * </p>
 */
public final class DbRoleSnapshotResolver {

    private DbRoleSnapshotResolver() {
    }

    /**
     * 解析用户当前在 DB 中的角色码。
     *
     * @param user 用户实体
     * @return {@code role_profile.code}；user 为 null 时返回 null
     */
    public static String resolveRoleCode(User user) {
        // SAFE: 本类是项目统一的数据库角色快照读取入口，专门封装 User.getRoleCode()，
        // 用于“选人后操作”场景（项目转移、任务分配等），与 UserPicker 数据源对齐。
        // 调用方应通过此类读取 DB 角色快照，不再直调 User.getRoleCode()。
        return user == null ? null : user.getRoleCode();
    }
}
