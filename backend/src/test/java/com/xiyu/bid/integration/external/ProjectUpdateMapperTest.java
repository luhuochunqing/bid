// Input: CRM 项目修改请求段（EvaluationBasicDTO / evaluationCustomerInfos 行 Map）
// Output: ProjectUpdateMapper 纯核心映射断言（字段映射/键归一/是否归一/TEL/role 回填）
// Pos: Test/合约验证 — 接口六 §3.6 纯核心逻辑
package com.xiyu.bid.integration.external;

import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProjectUpdateMapper} 纯核心映射单测（无 Spring 依赖，直接断言）。
 *
 * <p>覆盖接口文档 §3.6 关键语义：
 * <ul>
 *   <li>evaluationBasic 6 个标量字段 → 立项字段映射</li>
 *   <li>客户信息大写键归一（CONTACT→CONTACT_INFO、EVALUATION_BASIS→INFO_TENDENCY_BASIS）</li>
 *   <li>TEL（v3.12 新增）映射，未传保持 null</li>
 *   <li>是否类字段兼容"是/否"与 JSON 布尔并归一为 "true"/"false"</li>
 *   <li>role 按行下标对齐前端 14 个固定角色行</li>
 * </ul>
 */
class ProjectUpdateMapperTest {

    // ========== toInitiationPatch ==========

    @Test
    @DisplayName("basic 为 null 时返回空 patch（全部保留原值）")
    void toInitiationPatch_nullBasic_returnsEmptyPatch() {
        InitiationDto patch = ProjectUpdateMapper.toInitiationPatch(null);

        assertThat(patch.getAnnualEcommerceAmount()).isNull();
        assertThat(patch.getAnnualRevenue()).isNull();
        assertThat(patch.getRiskAssessment()).isNull();
        assertThat(patch.getRiskMitigationPlan()).isNull();
        assertThat(patch.getPmUnderstandsProcess()).isNull();
        assertThat(patch.getSupportNeeded()).isNull();
    }

    @Test
    @DisplayName("evaluationBasic 6 个标量字段映射到立项对应字段")
    void toInitiationPatch_mapsSixScalarFields() {
        EvaluationBasicDTO basic = new EvaluationBasicDTO(
                null,                       // plannedShortlistedCount 不映射
                new BigDecimal("1200.5"),   // mroOfficeFlowAmount
                "不利项文本",                // unfavorableItems 不映射
                "风险预判内容",              // riskAssessment
                "兜底方案内容",              // contingencyPlan
                "评标流程概述",              // processKnowledge
                "需要的支持",                // supportNotes
                new BigDecimal("88"),       // customerRevenue
                null);                      // projectPlanGapFiles 由服务层单独处理

        InitiationDto patch = ProjectUpdateMapper.toInitiationPatch(basic);

        assertThat(patch.getAnnualEcommerceAmount()).isEqualByComparingTo("1200.5");
        assertThat(patch.getAnnualRevenue()).isEqualByComparingTo("88");
        assertThat(patch.getRiskAssessment()).isEqualTo("风险预判内容");
        assertThat(patch.getRiskMitigationPlan()).isEqualTo("兜底方案内容");
        assertThat(patch.getPmUnderstandsProcess()).isEqualTo("评标流程概述");
        assertThat(patch.getSupportNeeded()).isEqualTo("需要的支持");
    }

    // ========== toCustomerInfoRows ==========

    @Test
    @DisplayName("TEL 键映射到 CustomerInfoRow.tel（v3.12 新增维度）")
    void toCustomerInfoRows_mapsTel() {
        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(
                List.of(Map.of("NAME", "张三", "TEL", "021-12345678")));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTel()).isEqualTo("021-12345678");
        assertThat(rows.get(0).getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("TEL 未传时保持 null（不用空串兜底）")
    void toCustomerInfoRows_telAbsent_staysNull() {
        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(
                List.of(Map.of("NAME", "张三")));

        assertThat(rows.get(0).getTel()).isNull();
        assertThat(rows.get(0).getName()).isEqualTo("张三");
        // 其余字段保持空串兜底口径
        assertThat(rows.get(0).getContactInfo()).isEmpty();
    }

    @Test
    @DisplayName("CONTACT 别名归一为 CONTACT_INFO（外部契约键兼容）")
    void toCustomerInfoRows_contactAliasMapped() {
        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(
                List.of(Map.of("CONTACT", "13800138000")));

        assertThat(rows.get(0).getContactInfo()).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("小写/带空格键经归一后生效；未知键静默忽略")
    void toCustomerInfoRows_keyNormalizationAndUnknownKeyIgnored() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put(" name ", "李四");                 // trim + 大写
        raw.put("evaluation_basis", "历史合作良好"); // 别名 → INFO_TENDENCY_BASIS → preferenceBasis
        raw.put("UNKNOWN_KEY", "应被忽略");

        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(List.of(raw));

        assertThat(rows.get(0).getName()).isEqualTo("李四");
        assertThat(rows.get(0).getPreferenceBasis()).isEqualTo("历史合作良好");
    }

    @Test
    @DisplayName("是否类字段兼容\"是/否\"与 JSON 布尔，归一为 true/false")
    void toCustomerInfoRows_yesNoAndBooleanNormalized() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("CONTACTED", "是");
        raw.put("GUIDED_BID", "否");
        raw.put("CAN_GET_KEY_INFO", Boolean.TRUE);
        raw.put("CAN_REMOVE_ADVERSE", Boolean.FALSE);
        raw.put("CAN_SYNC_EVAL", "true");
        raw.put("INFO_CLEAR_WINNER_BID", Boolean.TRUE);

        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(List.of(raw));

        CustomerInfoRow row = rows.get(0);
        assertThat(row.getReached()).isEqualTo("true");
        assertThat(row.getGuideBid()).isEqualTo("false");
        assertThat(row.getCanGetKeyInfo()).isEqualTo("true");
        assertThat(row.getCanRemoveAdverse()).isEqualTo("false");
        assertThat(row.getCanSyncEval()).isEqualTo("true");
        assertThat(row.getCanConfirmWin()).isEqualTo("true");
    }

    @Test
    @DisplayName("TENDENCY 原样透传（支持/中立/反对）")
    void toCustomerInfoRows_tendencyPassThrough() {
        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(
                List.of(Map.of("TENDENCY", "支持")));

        assertThat(rows.get(0).getPreference()).isEqualTo("支持");
    }

    @Test
    @DisplayName("role 按行下标回填 14 个固定角色；超出行 role 置空")
    void toCustomerInfoRows_roleByRowIndex() {
        List<Map<String, Object>> rawRows = List.of(
                Map.of("NAME", "决策人"),
                Map.of(),
                Map.of("NAME", "专家3"));

        List<CustomerInfoRow> three = ProjectUpdateMapper.toCustomerInfoRows(rawRows);
        assertThat(three.get(0).getRole()).isEqualTo("项目最高决策人");
        assertThat(three.get(1).getRole()).isEqualTo("物资公司董事长");
        assertThat(three.get(2).getRole()).isEqualTo("物资公司分管电商领导");

        // 第 14 行（索引 13）= 专家3；第 15 行（索引 14）超出固定角色行 → role 空串
        List<Map<String, Object>> overflow = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            overflow.add(Map.of());
        }
        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(overflow);
        assertThat(rows.get(13).getRole()).isEqualTo("专家3");
        assertThat(rows.get(14).getRole()).isEmpty();
    }

    @Test
    @DisplayName("null 行视为空行（保留 role 占位）；null 列表返回空列表")
    void toCustomerInfoRows_nullHandling() {
        assertThat(ProjectUpdateMapper.toCustomerInfoRows(null)).isEmpty();

        List<Map<String, Object>> withNullRow = new java.util.ArrayList<>();
        withNullRow.add(null);
        List<CustomerInfoRow> rows = ProjectUpdateMapper.toCustomerInfoRows(withNullRow);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRole()).isEqualTo("项目最高决策人");
        assertThat(rows.get(0).getName()).isEmpty();
    }
}
