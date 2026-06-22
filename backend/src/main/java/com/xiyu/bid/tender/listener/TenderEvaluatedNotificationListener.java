// Input: TenderStatusChangedEvent (AFTER_COMMIT)
// Output: 站内通知 + 待办（投标组长 + 投标管理员）
// Pos: Listener/标讯评估通知
package com.xiyu.bid.tender.listener;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.service.TenderEvaluationNotificationService;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 标讯状态变更通知监听器（CO-304）。
 * <p>监听 {@link TenderStatusChangedEvent}，当状态变为 EVALUATED 时发送审核通知。
 * <p>入口 A（TenderEvaluationSubmissionService.submit）已显式调用 notifier，不走此路径。
 * <p>本 listener 补齐其余 5 个入口的通知缺口：
 * <ul>
 *   <li>入口 B: 人工关联商机</li>
 *   <li>入口 C: CRM 推送回写</li>
 *   <li>入口 D: 集成 mapper</li>
 *   <li>入口 E: 评分分析审核</li>
 *   <li>入口 F: 批量状态变更</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderEvaluatedNotificationListener {

    private final TenderEvaluationNotificationService notificationService;
    private final TenderRepository tenderRepository;

    /**
     * 监听 TenderStatusChangedEvent，当状态变为 EVALUATED 时发送审核通知。
     * <p>仅处理 newStatus == EVALUATED 的事件，给投标组长和投标管理员发待办+通知。
     * <p>使用 AFTER_COMMIT 相位，与 WebhookEventListener 并行执行，各自独立 try/catch。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTenderEvaluated(TenderStatusChangedEvent event) {
        if (event.newStatus() != Tender.Status.EVALUATED) {
            return;
        }
        log.info("TenderEvaluatedNotificationListener received: tenderId={}, newStatus={}",
                event.tenderId(), event.newStatus());

        Tender tender = tenderRepository.findById(event.tenderId()).orElse(null);
        if (tender == null) {
            log.warn("Tender {} not found, skip notification", event.tenderId());
            return;
        }
        // 状态回退守卫：tender 当前状态不是 EVALUATED，说明已被其他操作修改
        if (tender.getStatus() != Tender.Status.EVALUATED) {
            log.info("Tender {} status is {} (not EVALUATED), skip notification to avoid stale alert",
                    event.tenderId(), tender.getStatus());
            return;
        }
        notificationService.createEvaluationNotificationTodos(tender);
    }
}