// Input: CRM 商机编号（或纯数字 id）
// Output: 标准化 CRM 商机编号（CC 前缀格式）
// Pos: webhook/infrastructure/
// 被 WebhookEventListener 和 ProjectResultConfirmedWebhookListener 共用。
package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import com.xiyu.bid.entity.Tender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
     * 解析标准化 CRM 商机编号（兼容旧调用，username 为空时 CRM 反查可能失败）。
     * @param crmOpportunityId 原始值（可能是纯数字 id 或 CC 前缀 code）
     * @return CC 前缀格式 code；无关联时返回空字符串
     */
    public String resolve(String crmOpportunityId) {
        return resolve(crmOpportunityId, null);
    }

    /**
     * 解析标准化 CRM 商机编号。
     * <p>CO-152: 传入操作者 username，使用其 OSS token 调用 CRM，避免依赖全局共享账号。
     *
     * @param crmOpportunityId 原始值（可能是纯数字 id 或 CC 前缀 code）
     * @param username 操作者用户名（用于 CRM 鉴权）；为空时 CRM 反查可能失败
     * @return CC 前缀格式 code；无关联时返回空字符串
     */
    public String resolve(String crmOpportunityId, String username) {
        if (crmOpportunityId == null || crmOpportunityId.isBlank()) {
            return "";
        }
        Long chanceId = tryParseChanceId(crmOpportunityId);
        if (chanceId == null) {
            // 非纯数字 → 已是 code 格式，直接返回
            return crmOpportunityId;
        }
        // 纯数字 → 调用 CRM 反查 code
        return lookupOpportunityCode(chanceId, username, "crmOpportunityId=" + crmOpportunityId);
    }

    /**
     * 从标讯解析 CRM 商机编号。
     * <p>优先使用 tender.crm_opportunity_id；为空时尝试从 externalId 的 sourceId 反查（外部推送场景）。
     *
     * @param tender 标讯实体
     * @param username 操作者用户名（用于 CRM 鉴权）
     * @return CC 前缀格式 code；无关联时返回空字符串
     */
    public String resolveFromTender(Tender tender, String username) {
        if (tender == null) {
            return "";
        }
        if (StringUtils.hasText(tender.getCrmOpportunityId())) {
            return resolve(tender.getCrmOpportunityId(), username);
        }
        // crm_opportunity_id 为空时，尝试用 externalId 的 sourceId 兜底反查
        String externalId = tender.getExternalId();
        if (externalId != null && externalId.startsWith("CRM:")) {
            String sourceId = externalId.substring(4);
            Long chanceId = tryParseChanceId(sourceId);
            if (chanceId != null) {
                return lookupOpportunityCode(chanceId, username, "externalId=" + externalId);
            }
        }
        return "";
    }

    private String lookupOpportunityCode(Long chanceId, String username, String context) {
        try {
            CrmProjectLeaderService.ProjectLeaderResult leader =
                    crmProjectLeaderService.findProjectLeaderByChanceId(chanceId, username);
            if (leader != null && StringUtils.hasText(leader.opportunityCode())) {
                log.info("CrmOpportunityCodeResolver: id={} → code={}, context={}", chanceId, leader.opportunityCode(), context);
                return leader.opportunityCode();
            }
            log.warn("CrmOpportunityCodeResolver: CRM returned no code for chanceId={}, context={}", chanceId, context);
        } catch (RuntimeException e) {
            log.error("CrmOpportunityCodeResolver: CRM lookup failed for chanceId={}, context={}: {}",
                    chanceId, context, e.getMessage());
        }
        // 降级：返回原值（数字 id），CRM 会返回 code:1 但至少有审计线索
        return String.valueOf(chanceId);
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
