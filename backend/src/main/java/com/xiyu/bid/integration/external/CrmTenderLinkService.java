package com.xiyu.bid.integration.external;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CRM 标讯自动关联服务。
 * <p>当外部系统（如 CRM）推送标讯时传入 crmId，负责：
 * <ol>
 *   <li>查询 CRM 项目负责人</li>
 *   <li>关联商机（设置 crmOpportunityId / crmOpportunityName）</li>
 *   <li>分配项目负责人（先按工号匹配本地用户，未匹配则用姓名兜底）</li>
 *   <li>将标讯状态设为 TRACKING</li>
 * </ol>
 *
 * <p>降级策略：
 * <ul>
 *   <li>CRM 接口异常：仅记录错误，保持标讯待分配状态（PENDING_ASSIGNMENT）</li>
 *   <li>未找到负责人：仍关联商机并设为跟踪中（TRACKING）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmTenderLinkService {

    private final CrmProjectLeaderService crmProjectLeaderService;
    private final UserRepository userRepository;

    /**
     * 当 crmId 非空时，查询项目负责人并关联商机。
     *
     * @param tender 标讯实体（已保存或即将保存）
     * @param crmId  CRM 商机编号，null/blank 时跳过
     */
    public void linkIfPresent(Tender tender, String crmId) {
        if (crmId == null || crmId.isBlank()) return;
        applyCrmLinkAndAssignment(tender, crmId);
    }

    /**
     * 查询项目负责人并关联商机。
     *
     * @param tender 标讯实体（已保存或即将保存）
     * @param crmId  CRM 商机编号
     */
    public void applyCrmLinkAndAssignment(Tender tender, String crmId) {
        log.info("Applying CRM link for tender id={}, crmId={}", tender.getId(), crmId);
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader =
                    crmProjectLeaderService.findProjectLeaderByChanceCode(crmId);
            if (leader == null) {
                log.warn("CRM link: no project leader found for crmId={}, linking opportunity and setting EVALUATED", crmId);
                // 未找到负责人：仍关联商机（用传入的 crmId），状态设为已评估
                tender.setCrmOpportunityId(crmId);
                tender.setStatus(Tender.Status.EVALUATED);
                return;
            }

            // 设置商机关联
            tender.setCrmOpportunityId(leader.opportunityCode());
            tender.setCrmOpportunityName(leader.opportunityName());

            // 解析项目负责人：先按工号匹配本地用户
            if (leader.projectLeaderNo() != null && !leader.projectLeaderNo().isBlank()) {
                userRepository.findByEmployeeNumber(leader.projectLeaderNo()).ifPresentOrElse(
                    user -> {
                        tender.setProjectManagerId(user.getId());
                        tender.setProjectManagerName(user.getFullName());
                        log.info("CRM link: assigned project manager id={}, name={} for crmId={}",
                                user.getId(), user.getFullName(), crmId);
                    },
                    () -> {
                        // 工号未匹配到本地用户，用姓名作为兜底
                        tender.setProjectManagerName(leader.projectLeaderName());
                        log.warn("CRM link: employeeNo={} not found locally, using name={} for crmId={}",
                                leader.projectLeaderNo(), leader.projectLeaderName(), crmId);
                    }
                );
            } else {
                // 无工号时直接用姓名
                tender.setProjectManagerName(leader.projectLeaderName());
                log.info("CRM link: no employeeNo, using name={} for crmId={}",
                        leader.projectLeaderName(), crmId);
            }

            // 将标讯状态设置为已评估
            tender.setStatus(Tender.Status.EVALUATED);
            log.info("CRM link: tender status set to EVALUATED for crmId={}", crmId);
        } catch (RuntimeException e) {
            log.error("CRM link failed for crmId={}, keeping PENDING_ASSIGNMENT: {}", crmId, e.getMessage());
            // 降级：CRM 接口异常时不中断主流程，仅记录错误
        }
    }
}
