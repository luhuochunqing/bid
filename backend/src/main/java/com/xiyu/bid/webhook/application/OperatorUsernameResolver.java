// Input: 操作者用户主键 / Tender 实体（creatorId + projectManagerId）
// Output: 操作者用户名（username），用于 webhook 投递时换 OSS token
// Pos: webhook/application/ - webhook 应用层共享组件
// Methods: resolve(Long) — 单 userId 反查 username
//          resolveDeliveryUsername(Tender, Long) — creatorId → projectManagerId → eventOperatorId（仅展示/审计）
//          resolveForCrmLookup(Tender, Long) — projectManagerId → creatorId → fallback（用于换 CRM token）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 操作者用户名解析器。
 * <p>通过 userId 反查 username，供 webhook 异步投递时取该用户的 OSS token 调 CRM generateToken，
 * 避免依赖全局共享账号（CO-152）。
 *
 * <p><b>CO-576 Phase B：</b>新增 {@link #resolveDeliveryUsername(Tender, Long)} 方法，
 * 按 creatorId → projectManagerId → eventOperatorId 顺序解析。
 * API Key 场景 event 常是 admin（有 username 无 OSS），故 creator/PM 优先于 event。
 *
 * <p>查不到或 userId 为空时返回 {@code null}，调用方按自身策略处理。
 *
 * <p><b>⚠️ 使用指引（防复发）：</b>
 * <ul>
 *   <li>{@link #resolveForCrmLookup(Tender, Long)}：用于"后续需要拿这个 username 去换 CRM token"的场景。
 *       解析顺序 PM → creator → fallback。原因：API Key 路径下 creator 常是 admin（无 OSS token），
 *       PM 是 CRM 推送的项目负责人（OSS 用户有 token）。</li>
 *   <li>{@link #resolveDeliveryUsername(Tender, Long)}：仅用于纯展示/审计场景，<b>不</b>进入 CRM token 换取链路。
 *       解析顺序 creator → PM → eventOperator。</li>
 * </ul>
 * <p>凡是 username 会进入 {@code CrmAuthService.getValidTokenForUser(username)} →
 * {@code WebhookCrmTokenResolver.resolveToken(username)} 链路的调用点，<b>必须</b>用 {@code resolveForCrmLookup}，
 * 否则在 {@code source_type=CRM_OPPORTUNITY} 且 creator=admin 的标讯上必触发 {@code TokenUnavailableException}。
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
     * 解析 webhook 投递用的 username（CO-576 Phase B）。
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
            if (StringUtils.isNotBlank(username)) {
                return username;
            }
            username = resolve(tender.getProjectManagerId());
            if (StringUtils.isNotBlank(username)) {
                return username;
            }
        }
        // 末位 fallback 也过滤 blank，保证返回值要么非 blank 要么 null
        String fallback = resolve(eventOperatorId);
        return StringUtils.isNotBlank(fallback) ? fallback : null;
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
            if (StringUtils.isNotBlank(username)) {
                return username;
            }
            // 其次用 creatorId（可能是真实用户）
            username = resolve(tender.getCreatorId());
            if (StringUtils.isNotBlank(username)) {
                return username;
            }
        }
        // 最后降级为 fallbackUserId（可能是 admin，无 OSS token）
        String fallback = resolve(fallbackUserId);
        return StringUtils.isNotBlank(fallback) ? fallback : null;
    }
}
