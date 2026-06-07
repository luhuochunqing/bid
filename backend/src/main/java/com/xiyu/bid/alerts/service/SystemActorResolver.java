// Input: UserRepository（admin/ADMIN 优先匹配）
// Output: 定时任务/系统事件用的 Long actorId
// Pos: alerts/service - 纯工具组件，从用户表解析 system 操作者
// 维护声明:
//   - 解析失败返回 null（不抛异常），由调用方决定是否终止业务流；
//   - 缓存到 volatile 字段，避免每次扫描都打 DB；
//   - 优先 username="admin" 兜底回退 enabled=1 的最早 ADMIN。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 解析"系统操作者"：定时任务、跨模块事件等无登录态场景写入
 * {@code notification.created_by} 等审计字段时使用的 user id。
 * <p>
 * 优先级：
 * <ol>
 *   <li>username="admin" 且 enabled=true</li>
 *   <li>enabled=true 的最早 ADMIN 角色用户</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class SystemActorResolver {

    private final UserRepository userRepository;

    /** 缓存版：启动时已解析；缺失时再查一次。 */
    private volatile Long cachedId;

    /**
     * 返回缓存中的 system actor id；若未缓存则解析并写回缓存。
     */
    public Long resolveCached() {
        Long cached = this.cachedId;
        if (cached != null) {
            return cached;
        }
        Long fresh = resolve();
        if (fresh != null) {
            this.cachedId = fresh;
        }
        return fresh;
    }

    /**
     * 实际解析逻辑：见类注释优先级。
     * 失败返回 null（不抛异常）。
     */
    Long resolve() {
        Long byAdmin = userRepository.findFirstByUsernameAndEnabledTrue("admin")
                .map(User::getId)
                .filter(Objects::nonNull)
                .orElse(null);
        if (byAdmin != null) {
            return byAdmin;
        }
        return userRepository.findFirstByRoleAndEnabledTrueOrderByIdAsc(User.Role.ADMIN)
                .map(User::getId)
                .filter(Objects::nonNull)
                .orElse(null);
    }
}
