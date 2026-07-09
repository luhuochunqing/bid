// Input: AlertHistory + AlertRule + extraPayload（告警历史、规则、附加载荷）
// Output: 无返回值；通过 NotificationApplicationService 发送站内通知
// Pos: alerts/service - 告警通知编排器（应用服务层，只编排不做决策）
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.domain.AlertMessagePolicy;
import com.xiyu.bid.alerts.domain.AlertNotificationInfo;
import com.xiyu.bid.alerts.domain.AlertRecipientPolicy;
import com.xiyu.bid.alerts.domain.RelatedIdParser;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.ProjectRepository;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 告警通知编排器：将 {@link AlertHistory} + {@link AlertRule} 编排为站内通知发送。
 *
 * <p>本类为应用服务层（FP-Java Profile：编排外壳），只做"找接收人 → 调通知服务"
 * 的串联，不做任何业务决策。业务决策由纯核心层（{@link AlertMessagePolicy} /
 * {@link AlertRecipientPolicy}）负责，本类只调用其结果。</p>
 *
 * <p><b>异常隔离</b>：通知失败不能影响告警主流程。{@link #dispatchNotification}
 * 方法体用 try-catch 包裹 {@link DataAccessException}（DB 故障，可降级），
 * 其他 {@link RuntimeException}（编程 bug）也 catch 但带完整 stacktrace 记录，
 * 不重抛。这保证告警扫描、告警历史写入等主流程不会被通知系统的故障拖垮。</p>
 *
 * <p><b>Sentry 上报</b>（CO-564）：编程 bug 级别的 {@link RuntimeException}（如 NPE）
 * 不仅写日志，还通过 {@code Sentry.captureException} 上报，让运维可主动发现告警链路
 * 静默失败。{@link DataAccessException} 属于 DB 故障降级，不上报（已有 DB 健康监控）。</p>
 *
 * <p><b>跳过条件</b>：
 * <ul>
 *   <li>接收人列表为空 → {@code log.warn} + return</li>
 *   <li>系统操作者未解析到（null）→ {@code log.warn} + return</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationOrchestrator {

    /** payload 中存放目标跳转 URL 的 key（与 NotificationApplicationService 约定一致）。 */
    private static final String PAYLOAD_KEY_TARGET_URL = "targetUrl";

    private final NotificationApplicationService notificationApplicationService;
    private final NotificationRecipientResolver notificationRecipientResolver;
    private final SystemActorResolver systemActorResolver;
    private final ProjectRepository projectRepository;
    private final AlertHistoryService alertHistoryService;

    /**
     * 编排告警通知发送。
     *
     * <p>编排步骤：
     * <ol>
     *   <li>调 {@link AlertRecipientPolicy#resolveRoleCodes} 获取接收人角色码</li>
     *   <li>调 {@link NotificationRecipientResolver#getUserIdsByRoleCodes} 解析为用户 ID</li>
     *   <li>DEADLINE 类型：额外解析标讯关联项目的成员，合并到接收人列表</li>
     *   <li>空列表 → 跳过</li>
     *   <li>调 {@link SystemActorResolver#resolveCached()} 获取系统操作者</li>
     *   <li>null → 跳过</li>
     *   <li>调 {@link AlertMessagePolicy#buildNotification} 生成通知内容</li>
     *   <li>构造 {@link CreateNotificationRequest} 并发送</li>
     * </ol>
     *
     * @param alertHistory 告警历史记录
     * @param alertRule    触发的告警规则
     * @param extraPayload 附加载荷（可为 null）
     */
    public void dispatchNotification(AlertHistory alertHistory, AlertRule alertRule,
                                     Map<String, Object> extraPayload) {
        try {
            // 1. 解析接收人角色码（纯核心）
            List<String> roleCodes = AlertRecipientPolicy.resolveRoleCodes(alertRule.getType());

            // 2. 解析为用户 ID
            List<Long> recipientUserIds = new ArrayList<>(
                    notificationRecipientResolver.getUserIdsByRoleCodes(roleCodes));

            // 3. DEADLINE 类型：补充标讯关联项目的成员，避免广播给所有项目Leader
            if (AlertRecipientPolicy.requiresProjectSpecificRecipients(alertRule.getType())) {
                addProjectSpecificRecipients(alertHistory, recipientUserIds);
            }

            // 3.5 CO-546: CA_EXPIRY 类型：将 extraPayload 中的 custodianId 加入接收人，
            // 保证 CA 保管员与 returnBorrow 路径一致地收到到期预警。
            addCustodianRecipientIfPresent(extraPayload, recipientUserIds);

            // 4. 接收人列表为空 → 跳过
            if (recipientUserIds.isEmpty()) {
                log.warn("跳过告警通知：无接收人，alertHistoryId={}, ruleType={}",
                        alertHistory.getId(), alertRule.getType());
                return;
            }

            // 5. 解析系统操作者
            Long systemActorId = systemActorResolver.resolveCached();

            // 6. 系统操作者为 null → 跳过
            if (systemActorId == null) {
                log.warn("跳过告警通知：系统操作者未解析到，alertHistoryId={}",
                        alertHistory.getId());
                return;
            }

            // 7. 生成通知内容（纯核心）
            AlertNotificationInfo info = AlertMessagePolicy.buildNotification(
                    alertRule.getType(),
                    alertHistory.getMessage(),
                    alertHistory.getRelatedId(),
                    extraPayload);

            // 8. 构造 payload（合并 extraPayload，targetUrl 由 extraPayload 主导）
            Map<String, Object> payload = buildPayload(extraPayload);

            // 9. 构造请求并发送
            CreateNotificationRequest request = new CreateNotificationRequest(
                    info.notificationType(),
                    info.sourceEntityType(),
                    info.sourceEntityId(),
                    info.title(),
                    info.body(),
                    payload,
                    recipientUserIds
            );

            // 10. 发送通知
            DispatchResult result = notificationApplicationService.createNotification(request, systemActorId);

            if (result != null && !result.isValid()) {
                log.warn("告警通知被通知服务拒绝：alertHistoryId={}, errorCode={}, errorMessage={}",
                        alertHistory.getId(), result.errorCode(), result.errorMessage());
            } else if (result != null) {
                log.info("告警通知发送成功：alertHistoryId={}, notificationId={}",
                        alertHistory.getId(), result.notificationId());
            }
        } catch (DataAccessException e) {
            // DB 故障：降级记录，不影响主流程（DB 健康另有监控，不上报 Sentry）
            log.warn("告警通知发送失败（DB 异常，降级跳过）：alertHistoryId={}, ruleType={}, error={}",
                    alertHistory.getId(), alertRule.getType(), e.getMessage());
        } catch (RuntimeException e) {
            // CO-564: 编程 bug（如 NPE）会导致告警通知静默失败，违背告警系统核心职责。
            // 不重抛（避免中断扫描主流程），但通过 Sentry 上报让运维可主动发现并修复。
            log.error("告警通知发送异常（非 DB 异常，已上报 Sentry）：alertHistoryId={}, ruleType={}, error={}",
                    alertHistory.getId(),
                    alertRule.getType(),
                    e.getMessage(),
                    e);
            Sentry.captureException(e);
        }
    }

    /**
     * 模板方法：创建告警历史并在新建时触发通知（消除 9 处重复模板）。
     *
     * <p>封装"createAlertHistoryIfAbsent → if created → dispatchNotification"模式，
     * 供所有扫描器统一调用。</p>
     *
     * @param request      告警历史创建请求
     * @param rule         告警规则
     * @param extraPayload 附加载荷（可为 null）
     * @return 创建结果（含是否新建标志）
     */
    public AlertHistoryCreateResult createAndNotifyIfNew(
            AlertHistoryCreateRequest request,
            AlertRule rule,
            Map<String, Object> extraPayload) {
        AlertHistoryCreateResult result = alertHistoryService.createAlertHistoryIfAbsent(request);
        if (result.created()) {
            dispatchNotification(result.alertHistory(), rule, extraPayload);
        }
        return result;
    }

    /**
     * DEADLINE 专用：解析标讯关联项目的成员，合并到接收人列表。
     *
     * <p>relatedId 格式为 "Tender:{id}"，通过 tenderId 查找关联项目，
     * 再调 {@link NotificationRecipientResolver#getProjectMemberUserIds} 获取项目成员。
     * 去重后合并到 recipientUserIds。</p>
     */
    private void addProjectSpecificRecipients(AlertHistory alertHistory, List<Long> recipientUserIds) {
        String relatedId = alertHistory.getRelatedId();
        // P1-2: 使用共享的 RelatedIdParser 统一解析 relatedId，消除 startsWith + substring 的脆弱实现
        Optional<Long> tenderIdOpt = RelatedIdParser.parseEntityId(
                RelatedIdParser.isEntityType(relatedId, "Tender") ? relatedId : null);
        if (tenderIdOpt.isEmpty()) {
            return;
        }
        Long tenderId = tenderIdOpt.get();
        // 查找关联项目（一个标讯可能对应多个项目，取第一个活跃项目）
        List<Project> projects = projectRepository.findByTenderId(tenderId);
        Set<Long> existingIds = Set.copyOf(recipientUserIds);
        for (Project project : projects) {
            List<Long> projectMembers = notificationRecipientResolver
                    .getProjectMemberUserIds(project.getId(), null);
            for (Long memberId : projectMembers) {
                if (!existingIds.contains(memberId)) {
                    recipientUserIds.add(memberId);
                }
            }
        }
    }

    /**
     * CO-546: 若 extraPayload 携带 custodianId，将其加入接收人列表（去重）。
     *
     * <p>CA 到期预警的定时扫描路径此前仅广播给投标管理员/投标组长，缺少 CA 保管员。
     * CaExpiryScanService 在 payload 中携带 custodianId，本方法将其合并进接收人列表，
     * 与 returnBorrow 路径的 CaNotificationDispatcher 接收人范围对齐。</p>
     *
     * <p>类型容错：payload 是 {@code Map<String, Object>}，custodianId 可能以 Number 形式传入，
     * 统一转 Long 比较去重。</p>
     */
    private void addCustodianRecipientIfPresent(Map<String, Object> extraPayload, List<Long> recipientUserIds) {
        if (extraPayload == null) return;
        Object raw = extraPayload.get("custodianId");
        if (raw == null) return;
        Long custodianId = toLongOrNull(raw);
        if (custodianId == null) return;
        if (!recipientUserIds.contains(custodianId)) {
            recipientUserIds.add(custodianId);
        }
    }

    private Long toLongOrNull(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 合并 extraPayload 为统一 payload。
     *
     * <p>targetUrl 完全由 extraPayload 主导，不再从 AlertNotificationInfo 覆盖（修复 P2-4）。
     * info.targetUrl() 和 extraPayload 中的 targetUrl 来自同一来源（resolveTargetUrl），
     * 无需双重写入。</p>
     *
     * @param extraPayload 调用方传入的附加载荷（可为 null）
     * @return 合并后的 payload；为空时返回 null
     */
    private Map<String, Object> buildPayload(Map<String, Object> extraPayload) {
        if (extraPayload == null || extraPayload.isEmpty()) {
            return null;
        }
        return new HashMap<>(extraPayload);
    }
}
