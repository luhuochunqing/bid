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
     * 当 sourceSystem=CRM 但未传 crmId（商机编号）时，用 sourceId（商机主键 id）
     * 兜底反查商机编号，再走关联流程。
     * <p>背景：CRM 创建标讯时天然知道商机，但推送时可能只传商机主键 id（作为 sourceId）
     * 而未传商机编号 code。此时标讯的 crmOpportunityId 为空，后续放弃/中标状态回传
     * 会因 code 为空而无法匹配 CRM 商机（tender 273 案例）。
     * <p>降级策略：sourceId 不是合法数字、或 CRM 反查失败时，保持原逻辑（不关联商机），
     * 不影响现有行为。
     *
     * @param tender       标讯实体
     * @param sourceSystem 来源系统（仅 "CRM" 触发兜底）
     * @param sourceId     来源系统数据 id（尝试解析为商机主键 id）
     * @return true 表示已成功通过兜底关联商机；false 表示未触发或反查失败
     */
    public boolean linkByChanceIdIfPresent(Tender tender, String sourceSystem, String sourceId) {
        if (sourceSystem == null || !"CRM".equals(sourceSystem)) return false;
        if (sourceId == null || sourceId.isBlank()) return false;
        Long chanceId;
        try {
            chanceId = Long.parseLong(sourceId.trim());
        } catch (NumberFormatException e) {
            // sourceId 不是纯数字，不是商机主键 id，跳过
            return false;
        }
        log.info("linkByChanceIdIfPresent: sourceId={} parsed as chanceId, tender id={}",
                sourceId, tender.getId());
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader =
                    crmProjectLeaderService.findProjectLeaderByChanceId(chanceId);
            if (leader == null || leader.opportunityCode() == null || leader.opportunityCode().isBlank()) {
                log.warn("linkByChanceIdIfPresent: no opportunity found for chanceId={}", chanceId);
                return false;
            }
            // 反查到商机，直接复用 leader 信息（避免再用 code 二次查询 page-list）
            applyLeaderAndStatus(tender, leader);
            return true;
        } catch (RuntimeException e) {
            log.error("linkByChanceIdIfPresent failed for chanceId={}: {}", chanceId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询项目负责人并关联商机。
     *
     * @param tender 标讯实体（已保存或即将保存）
     * @param crmId  CRM 商机编号 code，或商机主键 id（纯数字，CO-277 兼容）
     */
    public void applyCrmLinkAndAssignment(Tender tender, String crmId) {
        log.info("Applying CRM link for tender id={}, crmId={}", tender.getId(), crmId);
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader;
            // CO-277: CRM 推送的 crmOpportunityId 实测传的是商机主键 id（纯数字如 20916），
            // 而非编号 code（CC... 格式）。若是纯数字，按 id 反查详情拿 code；否则按 code 查 pageList。
            // 背景：之前一律按 code 查，id 格式必然反查失败，降级分支把 id 直接存入 crm_opportunity_id，
            // 导致后续 webhook 回传 payload code=20916，CRM 按编号匹配失败（tender 275 案例）。
            Long chanceId = tryParseChanceId(crmId);
            if (chanceId != null) {
                leader = crmProjectLeaderService.findProjectLeaderByChanceId(chanceId);
            } else {
                leader = crmProjectLeaderService.findProjectLeaderByChanceCode(crmId);
            }
            if (leader == null) {
                log.warn("CRM link: no project leader found for crmId={}, setting EVALUATED", crmId);
                // 未找到负责人时仍关联商机（状态设为已评估）。但仅当 crmId 是 code 格式时才直接存入——
                // 若是 id 格式，存 id 会让外层 linkByChanceIdIfPresent 兜底因"已有值"被跳过，
                // 且后续回传会用 id 当 code。保持 null 让兜底有机会用 sourceId 反查正确 code。
                if (chanceId == null) {
                    tender.setCrmOpportunityId(crmId);
                }
                tender.setStatus(Tender.Status.EVALUATED);
                return;
            }
            applyLeaderAndStatus(tender, leader);
        } catch (RuntimeException e) {
            log.error("CRM link failed for crmId={}, keeping PENDING_ASSIGNMENT: {}", crmId, e.getMessage());
            // 降级：CRM 接口异常时不中断主流程，仅记录错误
        }
    }

    /**
     * 尝试将 crmId 解析为商机主键 id。纯数字视为 id，非纯数字（如 CC20260619285）视为 code。
     *
     * @return 解析成功的 id；null 表示 crmId 是 code 格式
     */
    private Long tryParseChanceId(String crmId) {
        if (crmId == null || crmId.isBlank()) return null;
        try {
            return Long.parseLong(crmId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 用已查到的 leader 信息关联商机、分配负责人、设置状态。
     * <p>供 {@link #applyCrmLinkAndAssignment}（按 code 查）和
     * {@link #linkByChanceIdIfPresent}（按 id 查）共用，避免重复查询。
     */
    private void applyLeaderAndStatus(Tender tender, CrmProjectLeaderService.ProjectLeaderResult leader) {
        String crmId = leader.opportunityCode();
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
    }
}
