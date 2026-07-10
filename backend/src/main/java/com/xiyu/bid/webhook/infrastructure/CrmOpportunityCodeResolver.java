// Input: CRM 商机编号（或纯数字 id）
// Output: 标准化 CRM 商机编号（CC 前缀格式）；反查失败返回空字符串
// Pos: webhook/infrastructure/
// 被 TenderCrmOpportunityCodeResolver 调用。
package com.xiyu.bid.webhook.infrastructure;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CRM 商机编号解析器（基础设施层）。
 * <p>仅负责字符串级别的转换：若传入纯数字 id，调用 CRM detail 接口反查 CC 前缀 code；
 * 若已是 code 格式、为空、或反查失败，均直接返回，不降级为数字 id。
 * <p>CO-277 经验：CRM "code:0 success" 响应不可信，向 CRM 发送数字 id 会导致匹配失败，
 * 因此 webhook 发送侧反查失败时返回空字符串，避免伪成功。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrmOpportunityCodeResolver {

    private final CrmProjectLeaderService crmProjectLeaderService;

    /**
     * 解析标准化 CRM 商机编号。
     * <p>CO-152: 传入操作者 username，使用其 OSS token 调用 CRM，避免依赖全局共享账号。
     *
     * @param crmOpportunityId 原始值（可能是纯数字 id 或 CC 前缀 code）
     * @param username 操作者用户名（用于 CRM 鉴权）；为空时 CRM 反查可能失败
     * @return CC 前缀格式 code；无关联或反查失败时返回空字符串
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
        // webhook 发送侧不降级为数字 id，避免 CRM 伪成功（CO-277 教训）
        return "";
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
