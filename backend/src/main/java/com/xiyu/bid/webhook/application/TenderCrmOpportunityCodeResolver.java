// Input: Tender（含 crm_opportunity_id / externalId）+ 操作者 username
// Output: 标准化 CRM 商机编号（CC 前缀格式）；无关联或反查失败返回空字符串
// Pos: webhook/application/
// 被 WebhookEventListener / ProjectResultConfirmedWebhookListener 调用。
package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.external.ExternalSystemPrefix;
import com.xiyu.bid.webhook.infrastructure.CrmOpportunityCodeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 标讯维度的 CRM 商机编号解析器（应用层）。
 * <p>优先使用 tender.crm_opportunity_id（新数据始终是 code 格式）；
 * 为空时用 externalId 中 {@code CRM:sourceId} 的 sourceId 兜底反查 code（仅限旧数据）。
 * <p>纯字符串转换/CRM 调用委托给基础设施层 {@link CrmOpportunityCodeResolver}，
 * 避免 infrastructure 层掌握 Tender 实体与外部推送兜底规则（CO-152 / CO-277 设计修正）。
 *
 * <p><b>验收预期：</b>
 * <ul>
 *   <li>新数据：tender.crm_opportunity_id 已是 code 格式，直接返回，无需 CRM 调用。</li>
 *   <li>旧数据兜底：externalId 反查需有效的操作者 username；无 token 时返回空字符串。</li>
 *   <li>反查失败时不降级为原始数字 id，避免 CRM 侧"伪成功"（CO-277）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderCrmOpportunityCodeResolver {

    private final CrmOpportunityCodeResolver crmOpportunityCodeResolver;

    /**
     * 解析标讯关联的 CRM 商机编号。
     *
     * @param tender   当前标讯
     * @param username 操作者用户名（用于按用户 OSS token 调 CRM）；为空时反查可能失败
     * @return CC 前缀格式 code；无关联或反查失败时返回空字符串
     */
    public String resolveForTender(Tender tender, String username) {
        if (tender == null) {
            return "";
        }
        String crmOpportunityId = tender.getCrmOpportunityId();
        if (StringUtils.hasText(crmOpportunityId)) {
            return crmOpportunityCodeResolver.resolve(crmOpportunityId, username);
        }
        String sourceId = extractCrmSourceId(tender.getExternalId());
        if (sourceId == null) {
            return "";
        }
        return crmOpportunityCodeResolver.resolve(sourceId, username);
    }

    private String extractCrmSourceId(String externalId) {
        if (!StringUtils.hasText(externalId)) {
            return null;
        }
        String sourceId = ExternalSystemPrefix.CRM.extractSourceId(externalId);
        // 只有纯数字 sourceId 才可能通过 CRM chanceId 反查 code
        if (!StringUtils.hasText(sourceId) || !sourceId.chars().allMatch(Character::isDigit)) {
            log.warn("TenderCrmOpportunityCodeResolver: externalId '{}' has non-numeric sourceId, skip lookup", externalId);
            return null;
        }
        return sourceId;
    }
}
