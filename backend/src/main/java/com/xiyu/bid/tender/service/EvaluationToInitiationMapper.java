// Input: TenderEvaluationBasic + List<TenderEvaluationCustomerInfo>(EAV)
// Output: ProjectInitiationDetails 字段映射 + List<CustomerInfoRow>
// Pos: tender/service/ - CO-323 标讯评估表 → 项目立项映射（纯函数，无 Spring/JPA 依赖）
package com.xiyu.bid.tender.service;

import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CO-323: 标讯评估表数据 → 项目立项映射。
 * <p>复刻前端 {@code useInitiationStageActions.autoFillFromTender} 的字段映射
 * （INFO_KEY_MAP + ROW_ROLES），供 {@code TenderEvaluationService.proceedToBid}
 * 在创建项目时带入评估数据，避免前端 404 autofill 的刷新丢失问题。
 */
public final class EvaluationToInitiationMapper {

    private EvaluationToInitiationMapper() {
    }

    /**
     * 评估表基础信息 9 字段 → 立项实体（null 安全，只覆盖非空值）。
     * <p>customerRevenue → annualRevenue（@Deprecated 字段，spec 假设明确）。
     */
    public static void applyEvaluationBasic(ProjectInitiationDetails e, TenderEvaluationBasic b) {
        if (b == null) return;
        if (b.getPlannedShortlistedCount() != null) e.setExpectedBidders(b.getPlannedShortlistedCount());
        if (b.getMroOfficeFlowAmount() != null) e.setAnnualEcommerceAmount(b.getMroOfficeFlowAmount());
        if (b.getCustomerRevenue() != null) e.setAnnualRevenue(b.getCustomerRevenue());
        if (b.getUnfavorableItems() != null) e.setTenderAdverseItems(b.getUnfavorableItems());
        if (b.getRiskAssessment() != null) e.setRiskAssessment(b.getRiskAssessment());
        if (b.getContingencyPlan() != null) e.setRiskMitigationPlan(b.getContingencyPlan());
        if (b.getProcessKnowledge() != null) e.setPmUnderstandsProcess(b.getProcessKnowledge());
        if (b.getSupportNotes() != null) e.setSupportNeeded(b.getSupportNotes());
        if (b.getProjectPlanGap() != null) e.setProjectPlanGap(b.getProjectPlanGap());
    }

    /** 评估表客户信息 EAV infoKey → CustomerInfoRow 字段（对齐前端 INFO_KEY_MAP）。 */
    private static final Map<String, String> INFO_KEY_MAP = Map.ofEntries(
            Map.entry("NAME", "name"),
            Map.entry("CONTACT_INFO", "contactInfo"),
            Map.entry("POSITION", "position"),
            Map.entry("XIYU_CONTACT", "xiyuContact"),
            Map.entry("CONTACTED", "reached"),
            Map.entry("CONTACT_METHOD", "reachMethod"),
            Map.entry("INFO_TENDENCY_BASIS", "preferenceBasis"),
            // CO-323 fix: CRM 回填用 EVALUATION_BASIS（policy VALID_INFO_KEYS 允许），
            // 与 INFO_TENDENCY_BASIS 同列「倾向性评估依据」，补齐避免整列丢失。
            Map.entry("EVALUATION_BASIS", "preferenceBasis"),
            Map.entry("GUIDED_BID", "guideBid"),
            Map.entry("CAN_GET_KEY_INFO", "canGetKeyInfo"),
            Map.entry("CAN_REMOVE_ADVERSE", "canRemoveAdverse"),
            Map.entry("CAN_SYNC_EVAL", "canSyncEval"),
            Map.entry("TENDENCY", "preference"),
            Map.entry("INFO_CLEAR_WINNER_BID", "canConfirmWin"),
            Map.entry("INFO_WIN_RATE_IMPACT", "winRateImpact"));

    /** 立项客户信息矩阵 14 行角色（roleKey → 中文 role），顺序对齐前端 CUST_ROLES。 */
    private static final List<Map.Entry<String, String>> ROW_ROLES = List.of(
            Map.entry("PROJECT_HIGHEST_DECISION_MAKER", "项目最高决策人"),
            Map.entry("MATERIALS_COMPANY_CHAIRMAN", "物资公司董事长"),
            Map.entry("MATERIALS_COMPANY_ELECTRONICS_LEADER", "物资公司分管电商领导"),
            Map.entry("ELECTRONICS_COMPANY_CHAIRMAN", "电商公司董事长"),
            Map.entry("ELECTRONICS_COMPANY_GENERAL_MANAGER", "电商公司总经理"),
            Map.entry("ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER", "电商公司副总经理"),
            Map.entry("ELECTRONICS_COMPANY_OPERATIONS_LEADER", "电商公司运营负责人"),
            Map.entry("BID_DOCUMENT_PREPARER", "招标文件制作人"),
            Map.entry("OTHER_KEY_DECISION_MAKER_1", "其他关键决策人1"),
            Map.entry("OTHER_KEY_DECISION_MAKER_2", "其他关键决策人2"),
            Map.entry("OTHER_KEY_DECISION_MAKER_3", "其他关键决策人3"),
            Map.entry("EXPERT_1", "专家1"),
            Map.entry("EXPERT_2", "专家2"),
            Map.entry("EXPERT_3", "专家3"));

    /**
     * 评估表客户信息 EAV → 立项 14 行 CustomerInfoRow（按 ROW_ROLES 顺序）。
     * <p>按 roleKey 分组 EAV，每组填入对应 CustomerInfoRow 字段；无 EAV 数据的角色留空行。
     */
    public static List<CustomerInfoRow> toCustomerInfoRows(List<TenderEvaluationCustomerInfo> infos) {
        Map<String, Map<String, String>> byRole = new HashMap<>();
        if (infos != null) {
            for (TenderEvaluationCustomerInfo ci : infos) {
                String field = INFO_KEY_MAP.get(ci.getInfoKey());
                if (field == null) continue;
                byRole.computeIfAbsent(ci.getRoleKey(), k -> new LinkedHashMap<>())
                        .put(field, ci.getCellValue());
            }
        }
        List<CustomerInfoRow> rows = new ArrayList<>(ROW_ROLES.size());
        for (Map.Entry<String, String> role : ROW_ROLES) {
            Map<String, String> f = byRole.getOrDefault(role.getKey(), Collections.emptyMap());
            rows.add(CustomerInfoRow.builder()
                    .role(role.getValue())
                    .name(f.getOrDefault("name", ""))
                    .contactInfo(f.getOrDefault("contactInfo", ""))
                    .position(f.getOrDefault("position", ""))
                    .xiyuContact(f.getOrDefault("xiyuContact", ""))
                    .reached(f.getOrDefault("reached", ""))
                    .reachMethod(f.getOrDefault("reachMethod", ""))
                    .preference(f.getOrDefault("preference", ""))
                    .preferenceBasis(f.getOrDefault("preferenceBasis", ""))
                    .guideBid(f.getOrDefault("guideBid", ""))
                    .canGetKeyInfo(f.getOrDefault("canGetKeyInfo", ""))
                    .canRemoveAdverse(f.getOrDefault("canRemoveAdverse", ""))
                    .canSyncEval(f.getOrDefault("canSyncEval", ""))
                    .canConfirmWin(f.getOrDefault("canConfirmWin", ""))
                    .winRateImpact(f.getOrDefault("winRateImpact", ""))
                    .build());
        }
        return rows;
    }
}
