package com.xiyu.bid.tender.service;

import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-323: EvaluationToInitiationMapper 单测。
 * <p>验证 toCustomerInfoRows 返回动态行（只返回有 EAV 数据的角色行），
 * 与标讯 CustomerInfoMatrix.vue 的 .filter(hasCustomerInfoValue) 逻辑对齐。
 */
class EvaluationToInitiationMapperTest {

    @Test
    void toCustomerInfoRows_null_returnsEmpty() {
        // CO-323: null 输入返回 0 行（对标讯 0 行 × 14 列）
        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(null);
        assertThat(rows).isEmpty();
    }

    @Test
    void toCustomerInfoRows_emptyList_returnsEmpty() {
        // CO-323: 空列表返回 0 行（对标讯 0 行 × 14 列）
        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(List.of());
        assertThat(rows).isEmpty();
    }

    @Test
    void toCustomerInfoRows_singleRoleMultipleFields_returnsOneRow() {
        // CO-323: 单角色多字段 EAV → 1 行（动态行，不强制 14 行）
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("PROJECT_HIGHEST_DECISION_MAKER").infoKey("NAME").cellValue("张三").build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("PROJECT_HIGHEST_DECISION_MAKER").infoKey("CONTACT_INFO").cellValue("13800138000").build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRole()).isEqualTo("项目最高决策人");
        assertThat(rows.get(0).getName()).isEqualTo("张三");
        assertThat(rows.get(0).getContactInfo()).isEqualTo("13800138000");
    }

    @Test
    void toCustomerInfoRows_multipleRoles_returnsDynamicRows() {
        // CO-323: 2 个角色 EAV → 2 行（动态行，不是固定 14 行）
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("PROJECT_HIGHEST_DECISION_MAKER").infoKey("NAME").cellValue("张三").build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1").infoKey("NAME").cellValue("李四").build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRole()).isEqualTo("项目最高决策人");
        assertThat(rows.get(0).getName()).isEqualTo("张三");
        assertThat(rows.get(1).getRole()).isEqualTo("专家1");
        assertThat(rows.get(1).getName()).isEqualTo("李四");
    }

    @Test
    void toCustomerInfoRows_allFieldsMapped() {
        // CO-323: 验证 14 列字段全部正确映射
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("NAME").cellValue("王五").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("CONTACT_INFO").cellValue("wangwu@test.com").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("POSITION").cellValue("12").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("XIYU_CONTACT").cellValue("西域销售").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("CONTACT_METHOD").cellValue("1").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("INFO_TENDENCY_BASIS").cellValue("倾向依据").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("CONTACTED").cellValue("true").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("GUIDED_BID").cellValue("false").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("CAN_GET_KEY_INFO").cellValue("true").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("CAN_REMOVE_ADVERSE").cellValue("false").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("CAN_SYNC_EVAL").cellValue("true").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("TENDENCY").cellValue("1").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("INFO_CLEAR_WINNER_BID").cellValue("true").build(),
                TenderEvaluationCustomerInfo.builder().roleKey("EXPERT_1").infoKey("INFO_WIN_RATE_IMPACT").cellValue("3").build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(1);
        CustomerInfoRow row = rows.get(0);
        assertThat(row.getRole()).isEqualTo("专家1");
        assertThat(row.getName()).isEqualTo("王五");
        assertThat(row.getContactInfo()).isEqualTo("wangwu@test.com");
        assertThat(row.getPosition()).isEqualTo("12");
        assertThat(row.getXiyuContact()).isEqualTo("西域销售");
        assertThat(row.getReachMethod()).isEqualTo("1");
        assertThat(row.getPreferenceBasis()).isEqualTo("倾向依据");
        assertThat(row.getReached()).isEqualTo("true");
        assertThat(row.getGuideBid()).isEqualTo("false");
        assertThat(row.getCanGetKeyInfo()).isEqualTo("true");
        assertThat(row.getCanRemoveAdverse()).isEqualTo("false");
        assertThat(row.getCanSyncEval()).isEqualTo("true");
        assertThat(row.getPreference()).isEqualTo("1");
        assertThat(row.getCanConfirmWin()).isEqualTo("true");
        assertThat(row.getWinRateImpact()).isEqualTo("3");
    }
}
