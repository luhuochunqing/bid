// Input: CRM 项目修改请求段（EvaluationBasicDTO / evaluationCustomerInfos 行 Map）
// Output: InitiationDto 部分更新 patch + List<CustomerInfoRow>（含 tel）
// Pos: integration/external/ - 接口六项目修改纯核心映射（无 Spring/JPA 依赖，可单测）
// 维护声明: 客户信息键映射共用 EvaluationToInitiationMapper.INFO_KEY_MAP（单一真相源），
//          角色行顺序共用 EvaluationToInitiationMapper.ROLE_LABELS_IN_ORDER，新增维度需同批核对。
package com.xiyu.bid.integration.external;

import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.service.EvaluationToInitiationMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口六：项目修改（v3.12）纯核心映射。
 *
 * <p>字段命名与取值口径与标讯修改接口 §3.3 完全一致：键归一与是/否值归一
 * 复用 {@link TenderEvaluationIntegrationMapper} 的静态方法（单一真相源）。
 */
public final class ProjectUpdateMapper {

    private ProjectUpdateMapper() {
    }

    /**
     * evaluationBasic 段 → 立项部分更新 patch（仅映射文档 §3.6 列出的 6 个标量字段；
     * projectPlanGapFiles 由服务层按三态语义单独处理附件）。
     *
     * <p>映射关系：mroOfficeFlowAmount→annualEcommerceAmount、customerRevenue→annualRevenue、
     * riskAssessment→riskAssessment、contingencyPlan→riskMitigationPlan、
     * processKnowledge→pmUnderstandsProcess、supportNotes→supportNeeded。
     * <p>{@code basic} 为 null 时返回空 patch（全部保留原值）。
     */
    @SuppressWarnings("deprecation")
    public static InitiationDto toInitiationPatch(EvaluationBasicDTO basic) {
        if (basic == null) {
            return InitiationDto.builder().build();
        }
        return InitiationDto.builder()
                .annualEcommerceAmount(basic.mroOfficeFlowAmount())
                .annualRevenue(basic.customerRevenue())
                .riskAssessment(basic.riskAssessment())
                .riskMitigationPlan(basic.contingencyPlan())
                .pmUnderstandsProcess(basic.processKnowledge())
                .supportNeeded(basic.supportNotes())
                .build();
    }

    /**
     * CRM 客户信息行数组 → 立项 CustomerInfoRow 列表（全量替换语义由调用方保证）。
     *
     * <p>规则：
     * <ul>
     * <li>键归一：trim + 大写 + 别名（CONTACT→CONTACT_INFO、EVALUATION_BASIS→INFO_TENDENCY_BASIS），
     *       与标讯修改接口 §3.3 同一实现；字段映射共用 {@link EvaluationToInitiationMapper#INFO_KEY_MAP}</li>
     *   <li>未知键静默忽略（对齐 §3.3 白名单语义）</li>
     *   <li>是否类字段（CONTACTED/GUIDED_BID/...）兼容"是/否"与 JSON 布尔，归一为 "true"/"false"</li>
     *   <li>TENDENCY 原样透传（"支持"/"中立"/"反对"），INFO_CLEAR_WINNER_BID 归一为 "true"/"false"</li>
     *   <li>role 按行下标对齐前端 14 个固定角色行；超出行（索引 ≥ 14）role 置空</li>
     * </ul>
     */
    public static List<CustomerInfoRow> toCustomerInfoRows(List<Map<String, Object>> crmRows) {
        List<CustomerInfoRow> rows = new ArrayList<>();
        if (crmRows == null) {
            return rows;
        }
        for (int i = 0; i < crmRows.size(); i++) {
            rows.add(toCustomerInfoRow(crmRows.get(i), roleForIndex(i)));
        }
        return rows;
    }

    /** 单行映射：null 行视为空行（保留 role 占位，维持行顺序与前端表格一致）。 */
    private static CustomerInfoRow toCustomerInfoRow(Map<String, Object> raw, String role) {
        Map<String, String> f = new HashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String infoKey = TenderEvaluationIntegrationMapper.normalizeCustomerInfoKey(entry.getKey());
                String field = infoKey == null ? null : EvaluationToInitiationMapper.INFO_KEY_MAP.get(infoKey);
                if (field == null) {
                    continue;
                }
                f.put(field, TenderEvaluationIntegrationMapper.normalizeCustomerInfoValue(infoKey, entry.getValue()));
            }
        }
        return CustomerInfoRow.builder()
                .role(role)
                .name(f.getOrDefault("name", ""))
                .contactInfo(f.getOrDefault("contactInfo", ""))
                // TEL 为 v3.12 新增维度，未传时保持 null（与 EvaluationToInitiationMapper 口径一致）
                .tel(f.get("tel"))
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
                .build();
    }

    /** 行下标 → 角色中文名（共用 {@link EvaluationToInitiationMapper#ROLE_LABELS_IN_ORDER}）；索引 ≥ 14 时返回空串。 */
    private static String roleForIndex(int index) {
        List<String> roles = EvaluationToInitiationMapper.ROLE_LABELS_IN_ORDER;
        return index < roles.size() ? roles.get(index) : "";
    }
}
