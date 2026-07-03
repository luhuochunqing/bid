package com.xiyu.bid.integration.external;

import java.util.List;
import java.util.Map;

/**
 * CRM 商机负责人优先：用 tender.projectManagerName 覆盖 CRM 推送的 customerInfos 中的 XIYU_CONTACT 字段。
 *
 * <p>背景：CRM 推送的 evaluation.customerInfo 里 XIYU_CONTACT 字段可能不是当前商机负责人，
 * 需要用 tender.projectManagerName（来自 CRM 商机接口 leader）覆盖，保持一致性。
 *
 * <p>历史教训：PR #1179 修了 tenders.project_manager_id 的覆盖问题，但未修
 * tender_evaluation_customer_info.XIYU_CONTACT 的覆盖问题，导致 /bidding/931 复发。
 * 详见 docs/lessons/crm-integration-lessons.md §12。
 */
public final class XiyuContactOverride {

    /** 客户信息矩阵中"西域项目负责人"列的字段名 */
    public static final String XIYU_CONTACT_KEY = "XIYU_CONTACT";

    private XiyuContactOverride() {}

    /**
     * 用 projectManagerName 覆盖 customerInfos 中所有 XIYU_CONTACT 字段。
     *
     * <p>覆盖规则：
     * <ul>
     *   <li>customerInfos 为 null 或空 → 不处理</li>
     *   <li>projectManagerName 为 null 或空白 → 不处理（保留 CRM 原值，避免误清空）</li>
     *   <li>仅覆盖已存在的 XIYU_CONTACT key，不新增</li>
     * </ul>
     *
     * @param customerInfos CRM 推送的评估表客户信息（可能为 null）
     * @param projectManagerName 标讯项目负责人名称（来自 CRM 商机 leader）
     */
    public static void apply(List<Map<String, Object>> customerInfos, String projectManagerName) {
        if (customerInfos == null || projectManagerName == null || projectManagerName.isBlank()) {
            return;
        }
        for (Map<String, Object> row : customerInfos) {
            if (row == null) continue;
            if (row.containsKey(XIYU_CONTACT_KEY)) {
                row.put(XIYU_CONTACT_KEY, projectManagerName);
            }
        }
    }
}
