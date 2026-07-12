// Input: 操作者用户主键 / Tender 实体（creatorId + projectManagerId）
// Output: 操作者用户名（username），用于 webhook 投递时换 OSS token
// Pos: webhook/application/ - webhook 应用层共享组件
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 操作者用户名解析器。
 * <p>通过 userId 反查 username，供 webhook 异步投递时取该用户的 OSS token 调 CRM generateToken，
 * 避免依赖全局共享账号（CO-152）。
 *
 * <p><b>CO-571 Phase B：</b>新增 {@link #resolveDeliveryUsername(Tender, Long)} 方法，
 * 按 creatorId → projectManagerId → eventOperatorId 顺序解析。
 * API Key 场景 event 常是 admin（有 username 无 OSS），故 creator/PM 优先于 event。
 *
 * <p>查不到或 userId 为空时返回 {@code null}，调用方按自身策略处理。
 */
@Component
@RequiredArgsConstructor
public class OperatorUsernameResolver {

    private final UserRepository userRepository;

    /**
     * 解析操作者用户名。
     *
     * @param operatorId 操作者用户主键
     * @return 用户名；userId 为空或查不到时返回 {@code null}
     */
    public String resolve(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        return userRepository.findById(operatorId)
                .map(User::getUsername)
                .orElse(null);
    }

    /**
     * 解析 webhook 投递用的 username（CO-571 Phase B）。
     * <p>解析顺序：tender.creatorId → tender.projectManagerId → eventOperatorId。
     * <p>API Key 场景 event 常是 admin（有 username 无 OSS），故 creator/PM 优先于 event。
     * operatorName 仅展示，不参与 token，B/C 验收不写 name。
     *
     * @param tender 标讯实体（提供 creatorId / projectManagerId）
     * @param eventOperatorId 事件中的操作者 ID
     * @return 第一个能解析到非空 username 的用户名；全 miss 返回 {@code null}
     */
    public String resolveDeliveryUsername(Tender tender, Long eventOperatorId) {
        if (tender != null) {
            String username = resolve(tender.getCreatorId());
            if (isNotBlank(username)) {
                return username;
            }
            username = resolve(tender.getProjectManagerId());
            if (isNotBlank(username)) {
                return username;
            }
        }
        return resolve(eventOperatorId);
    }

    /**
     * 解析 CRM 反查用的 username（CO-277 第 6 次修复）。
     * <p>解析顺序：tender.projectManagerId → tender.creatorId → fallbackUserId。
     * <p>与 {@link #resolveDeliveryUsername} 的区别：projectManagerId 优先于 creatorId。
     * 原因：API Key 路径下 creatorId 常是 admin（系统账号无 OSS token），
     * 而 projectManagerId 是 CRM 推送的项目负责人（OSS 用户有 OSS token）。
     * CRM 反查商机关联需要 OSS token → generateToken → CRM JWT，必须用有 OSS token 的用户。
     *
     * @param tender 标讯实体（提供 projectManagerId / creatorId）
     * @param fallbackUserId 降级用 userId（通常是 API Key 创建者 admin）
     * @return 第一个能解析到非空 username 的用户名；全 miss 返回 {@code null}
     */
    public String resolveForCrmLookup(Tender tender, Long fallbackUserId) {
        if (tender != null) {
            // 优先用项目负责人（OSS 用户，有 OSS token）
            String username = resolve(tender.getProjectManagerId());
            if (isNotBlank(username)) {
                return username;
            }
            // 其次用 creatorId（可能是真实用户）
            username = resolve(tender.getCreatorId());
            if (isNotBlank(username)) {
                return username;
            }
        }
        // 最后降级为 fallbackUserId（可能是 admin，无 OSS token）
        return resolve(fallbackUserId);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
