package com.xiyu.bid.formengine.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CO-601 US2：项目三 scope schema key 冲突纯函数校验（契约 §5，FR-006）。
 *
 * <p>语义边界（contracts/project-custom-fields-api.md §5）：
 * <ul>
 *   <li>hybrid scope（project.initiation / project.detail）：预置字段由业务页 fallback 硬编码渲染，
 *       schema 不应含预置 key —— 命中即拒绝；</li>
 *   <li>project.basic（纯 schema 渲染，V140 种子含 8 预置字段）：预置字段合法存在，仅校验 key 重复
 *       （新增撞预置 key 必然产生重复 key，已被重复校验覆盖）——避免老 schema 重保存被误杀；</li>
 *   <li>非项目 scope：不校验，直接放行。</li>
 * </ul>
 *
 * <p>⚠️ 互指注释：前端 workflowFormDesignerCore.js 的 PROJECT_LOCKED_FIELD_KEYS 内嵌同一清单，
 * 改动必须双向同步。
 */
public final class CustomFieldsSchemaPolicy {

    public static final String SCOPE_BASIC = "project.basic";
    public static final String SCOPE_INITIATION = "project.initiation";
    public static final String SCOPE_DETAIL = "project.detail";

    /** 项目三 scope 预置字段清单（与前端 PROJECT_LOCKED_FIELD_KEYS 同一来源）。 */
    public static final Map<String, Set<String>> PROJECT_LOCKED_FIELD_KEYS = Map.of(
            SCOPE_BASIC, Set.of(
                    "name", "customer", "budget", "industry", "region", "platform",
                    "deadline", "manager", "competitors"),
            SCOPE_DETAIL, Set.of(
                    "description", "tags", "startDate", "endDate", "remark",
                    "projectLeaderName", "leaderDepartment"),
            SCOPE_INITIATION, Set.of(
                    "projectName", "ownerUnit", "createTime", "projectType", "customerType",
                    "priorityLevel", "headquartersLocation", "projectLeaderName", "projectLeaderUserId",
                    "leaderDepartment", "contactName", "contactPhone", "contactTel", "contactMail",
                    "contactName2", "contactPhone2", "contactTel2", "contactMail2",
                    "tenderId", "expectedBidders", "annualEcommerceAmount", "annualRevenue", "customerRevenue",
                    "bidOpenTime", "bidMonth", "biddingPlatform",
                    "needDeposit", "depositAmount", "depositPaymentMethod", "depositDueDate",
                    "tenderAdverseItems", "riskAssessment", "riskMitigationPlan", "pmUnderstandsProcess",
                    "supportNeeded", "projectPlanGap", "projectPlanGapFiles",
                    "tenderDocumentId", "aiRiskLevel", "aiRiskAssessmentNotes",
                    "biddingLeaderName", "biddingAssistantName",
                    "custFixedRows", "customerInfoRows"));

    /** hybrid scope：预置字段由业务页 fallback 渲染，schema 不应含预置 key。 */
    private static final Set<String> HYBRID_SCOPES = Set.of(SCOPE_INITIATION, SCOPE_DETAIL);

    private CustomFieldsSchemaPolicy() {
    }

    /**
     * 校验 schema fields 的 key 冲突。
     *
     * @param scope  表单 scope
     * @param fields schema 中的 fields 数组（Map 形态，含 key/label/type 等；非 Map 元素自动跳过）
     * @return ValidationResult — 非项目 scope / 空 fields 直接 success
     */
    public static ValidationResult validate(String scope, List<? extends Map<?, ?>> fields) {
        Set<String> presetKeys = PROJECT_LOCKED_FIELD_KEYS.get(scope);
        if (presetKeys == null || fields == null || fields.isEmpty()) {
            return ValidationResult.success();
        }

        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> field : fields) {
            if (field == null || !(field.get("key") instanceof String key) || key.isBlank()) {
                continue;
            }
            if (!seen.add(key)) {
                errors.add("字段 key 重复: " + key);
            } else if (HYBRID_SCOPES.contains(scope) && presetKeys.contains(key)) {
                errors.add("自定义字段 key 命中预置清单: " + key);
            }
        }
        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}
