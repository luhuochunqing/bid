package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CO-297: 标讯关联 CRM 商机的占位校验器。
 * <p>
 * 单一职责：仅负责"该 CRM 商机是否已被其他标讯占用"的查询与判定，
 * 不参与事务、权限、DTO 映射、状态机等其他职责。
 * <p>
 * 抽离原因：避免 TenderCommandService 单文件超 300 行 line-budget 上限。
 */
@Component
@RequiredArgsConstructor
public class TenderCrmLinkGuard {

    private final TenderRepository tenderRepository;

    /**
     * 检查 CRM 商机是否已被其他标讯关联。
     *
     * @param currentTenderId 当前操作的标讯 ID（自身占用不算冲突）
     * @param crmOpportunityId 要关联的 CRM 商机 ID
     * @throws BusinessException 409 当商机已被其他标讯占用时
     */
    public void assertCrmOpportunityNotOccupied(Long currentTenderId, String crmOpportunityId) {
        if (crmOpportunityId == null || crmOpportunityId.isBlank()) {
            return;
        }
        tenderRepository.findFirstByCrmOpportunityId(crmOpportunityId).ifPresent(occupied -> {
            if (!occupied.getId().equals(currentTenderId)) {
                throw new BusinessException(409,
                        "该 CRM 商机已被其他标讯关联（标讯 ID: " + occupied.getId() + "），请先解除原关联");
            }
        });
    }
}
