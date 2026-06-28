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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 标讯状态同步服务：项目结果登记后将标讯状态同步到与项目结果一致的终态。
 *
 * <p>背景：标讯转项目后 {@link Tender#getProjectId()} 建立了关联，但标讯状态
 * 可能停留在 TRACKING/BIDDING 等中间态，项目侧状态变更不会回写标讯，导致标讯状态断链。
 * 本服务在项目结果登记（WON/LOST/FAILED/ABANDONED）时同步标讯状态到终态。
 *
 * <p>这是系统内部同步（非用户直接操作标讯），因此：
 * <ul>
 *   <li>跳过标讯权限校验（项目结果登记已通过项目权限校验）</li>
 *   <li>发布 {@link TenderStatusChangedEvent}（让 CRM webhook 也收到同步）</li>
 *   <li>幂等：已是目标状态或已是终态则跳过</li>
 *   <li>事务隔离：使用 {@link Propagation#REQUIRES_NEW} 独立子事务，同步失败不回滚外层项目结果登记事务</li>
 *   <li>状态机绕过：对于非终态标讯（如 TRACKING），直接 setStatus 到目标终态——
 *       项目结果登记已通过业务校验，标讯同步是数据一致性兜底，不强制走状态机流转路径</li>
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
     * <p>使用 {@link Propagation#REQUIRES_NEW} 开启独立子事务：
     * 即使本方法抛出 RuntimeException，也只回滚子事务，不影响外层项目结果登记事务。
     * 外层 {@code ProjectResultRegistrationService.register()} 的 try-catch 可以正确隔离异常。
     *
     * @param tenderId  标讯 ID（为 null 时跳过，兼容未关联标讯的项目）
     * @param bidResult 项目结果类型
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        // 幂等：已是其他终态则跳过（避免终态间非法流转）
        // 注意：此检查是 canTransition 的子集（终态的 allowedTargets 为空集），但保留以提供更精确的日志
        if (TenderStatusTransitionPolicy.isTerminal(tender.getStatus())) {
            log.warn("syncFromProjectResult: tender {} already in terminal status {}, cannot sync to {}",
                    tenderId, tender.getStatus(), targetStatus);
            return;
        }

        // 系统内部同步：对于非终态标讯，直接流转到目标终态。
        // 项目结果登记已通过业务校验，标讯同步是数据一致性兜底，不强制走状态机流转路径
        // （标讯可能因上游流程缺失停留在 TRACKING，但项目结果已出，标讯应同步到终态）。
        // canTransition 仅用于日志辅助，不再阻止同步。
        if (!TenderStatusTransitionPolicy.canTransition(tender.getStatus(), targetStatus)) {
            log.warn("syncFromProjectResult: tender {} status {} cannot transition to {} via state machine, forcing sync (system internal)",
                    tenderId, tender.getStatus(), targetStatus);
        }
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
     * 项目结果类型 -> 标讯状态映射。Tender 无 FAILED 状态，流标归一到 LOST。
     *
     * <p>注：此方法为纯函数（无副作用、不依赖 Spring），理论上应属于纯核心层。
     * 但移动到 {@link TenderStatusTransitionPolicy} 会引入 {@code batch.core} 对
     * {@code project.core} 的跨包依赖，违反架构边界。作为折中，保留在 Service 中
     * 但标记为 static，确保可独立单测。
     */
    static Tender.Status mapToTenderStatus(BidResultType bidResult) {
        return switch (bidResult) {
            case WON -> Tender.Status.WON;
            case LOST, FAILED -> Tender.Status.LOST;
            case ABANDONED -> Tender.Status.ABANDONED;
        };
    }
}
