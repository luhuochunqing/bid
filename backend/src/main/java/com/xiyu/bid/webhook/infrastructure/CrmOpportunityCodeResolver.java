// Input: CRM 商机编号（或纯数字 id）
// Output: 标准化 CRM 商机编号（CC 前缀格式）
// Pos: webhook/infrastructure/
// 被 WebhookEventListener 和 ProjectResultConfirmedWebhookListener 共用。
package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CRM 商机编号解析器。
 * <p>tender.crm_opportunity_id 可能存的是商机主键 id（纯数字如 20942），
 * 而非商机编号 code（CC 前缀如 CC20260621323）。CRM bidInfoSync 接口期望 code 格式。
 * <p>若传入纯数字 id，调用 CRM detail 接口反查 code；
 * 反查失败则降级用原值（CRM 可能仍返回 code:1，但至少有审计线索）。
 * <p>若已是 CC 前缀格式或为空，直接返回。
 * <p>CO-277 经验：CRM "code:0 success" 响应不可信，必须验证业务结果。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrmOpportunityCodeResolver {

    private final CrmProjectLeaderService crmProjectLeaderService;

    /**
     * 解析标准化 CRM 商机编号。
     * @param crmOpportunityId 原始值（可能是纯数字 id 或 CC 前缀 code）
     * @return CC 前缀格式 code；无关联时返回空字符串
     */
    public String resolve(String crmOpportunityId) {
        if (crmOpportunityId == null || crmOpportunityId.isBlank()) {
            return "";
        }
        Long chanceId = tryParseChanceId(crmOpportunityId);
        if (chanceId == null) {
            // 非纯数字 → 已是 code 格式，直接返回
            return crmOpportunityId;
        }
        // 纯数字 → 调用 CRM 反查 code
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader =
                    crmProjectLeaderService.findProjectLeaderByChanceId(chanceId);
            if (leader != null && leader.opportunityCode() != null && !leader.opportunityCode().isBlank()) {
                log.info("CrmOpportunityCodeResolver: id={} → code={}", chanceId, leader.opportunityCode());
                return leader.opportunityCode();
            }
            log.warn("CrmOpportunityCodeResolver: CRM returned no code for chanceId={}, using raw id as fallback", chanceId);
        } catch (RuntimeException e) {
            log.error("CrmOpportunityCodeResolver: CRM lookup failed for chanceId={}, using raw id as fallback: {}",
                    chanceId, e.getMessage());
        }
        // 降级：返回原值（数字 id），CRM 会返回 code:1 但至少有审计线索
        return crmOpportunityId;
    }

    private Long tryParseChanceId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
