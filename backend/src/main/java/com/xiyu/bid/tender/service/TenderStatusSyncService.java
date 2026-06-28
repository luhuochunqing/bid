package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.core.TenderStatusTransitionPolicy;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.webhook.domain.TenderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 标讯状态同步服务：项目结果登记后将标讯状态同步到与项目结果一致的终态。
 *
 * <p>背景：标讯转项目后 {@link Tender#getProjectId()} 建立了关联，但标讯状态
 * 一直停在 BIDDING，项目侧状态变更不会回写标讯，导致标讯状态断链。本服务在
 * 项目结果登记（WON/LOST/FAILED/ABANDONED）时同步标讯状态。
 *
 * <p>这是系统内部同步（非用户直接操作标讯），因此：
 * <ul>
 *   <li>跳过标讯权限校验（项目结果登记已通过项目权限校验）</li>
 *   <li>保留 {@link TenderStatusTransitionPolicy} 流转校验（不绕过状态机规则）</li>
 *   <li>发布 {@link TenderStatusChangedEvent}（让 CRM webhook 也收到同步）</li>
 *   <li>幂等：已是目标状态或已是终态则跳过</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderStatusSyncService {

    private final TenderRepository tenderRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 项目结果登记后同步标讯状态到与项目结果一致的终态。
     *
     * @param tenderId  标讯 ID（为 null 时跳过，兼容未关联标讯的项目）
     * @param bidResult 项目结果类型
     */
    @Transactional
    public void syncFromProjectResult(Long tenderId, BidResultType bidResult) {
        if (tenderId == null || bidResult == null) {
            log.debug("syncFromProjectResult skipped: tenderId={}, bidResult={}", tenderId, bidResult);
            return;
        }
        Optional<Tender> tenderOpt = tenderRepository.findById(tenderId);
        if (tenderOpt.isEmpty()) {
            log.warn("syncFromProjectResult: tender {} not found, skip", tenderId);
            return;
        }
        Tender tender = tenderOpt.get();
        Tender.Status targetStatus = mapToTenderStatus(bidResult);

        // 幂等：已是目标状态则跳过
        if (tender.getStatus() == targetStatus) {
            log.debug("syncFromProjectResult: tender {} already in target status {}, skip", tenderId, targetStatus);
            return;
        }
        // 幂等：已是其他终态则跳过（避免终态间非法流转抛异常）
        if (isTerminal(tender.getStatus())) {
            log.warn("syncFromProjectResult: tender {} already in terminal status {}, cannot sync to {}",
                    tenderId, tender.getStatus(), targetStatus);
            return;
        }

        // 流转校验（不绕过状态机规则，BIDDING → WON/LOST/ABANDONED 才合法）
        TenderStatusTransitionPolicy.assertTransition(tender.getStatus(), targetStatus);
        Tender.Status previousStatus = tender.getStatus();
        tender.setStatus(targetStatus);
        tenderRepository.save(tender);

        // 发布事件，让 CRM webhook 也收到同步
        eventPublisher.publishEvent(TenderStatusChangedEvent.of(
                tender.getId(), tender.getExternalId(), previousStatus, targetStatus, tender.getTitle()));

        log.info("syncFromProjectResult: tender {} status synced {} -> {} (project result={})",
                tenderId, previousStatus, targetStatus, bidResult);
    }

    /**
     * 项目结果类型 → 标讯状态映射。Tender 无 FAILED 状态，流标归一到 LOST。
     */
    static Tender.Status mapToTenderStatus(BidResultType bidResult) {
        return switch (bidResult) {
            case WON -> Tender.Status.WON;
            case LOST, FAILED -> Tender.Status.LOST;
            case ABANDONED -> Tender.Status.ABANDONED;
        };
    }

    private static boolean isTerminal(Tender.Status status) {
        return status == Tender.Status.WON || status == Tender.Status.LOST || status == Tender.Status.ABANDONED;
    }
}
