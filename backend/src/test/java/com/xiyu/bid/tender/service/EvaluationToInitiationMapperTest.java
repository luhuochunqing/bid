// Input: TenderEvaluationCustomerInfo (EAV) → EvaluationToInitiationMapper.toCustomerInfoRows
// Output: 验证动态行逻辑：只返回有EAV数据的角色，无数据时返回空列表
// Pos: backend/src/test/java/com/xiyu/bid/tender/service/

package com.xiyu.bid.tender.service;

import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo.ValueType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationToInitiationMapperTest {

    @Test
    void toCustomerInfoRows_returnsEmptyWhenNullInput() {
        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(null);
        assertThat(rows).isNotNull().isEmpty();
    }

    @Test
    void toCustomerInfoRows_returnsEmptyWhenEmptyList() {
        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(Collections.emptyList());
        assertThat(rows).isNotNull().isEmpty();
    }

    @Test
    void toCustomerInfoRows_returnsSingleRowForSingleRoleWithData() {
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("PROJECT_HIGHEST_DECISION_MAKER")
                        .infoKey("NAME")
                        .cellValue("张总")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("PROJECT_HIGHEST_DECISION_MAKER")
                        .infoKey("CONTACT_INFO")
                        .cellValue("13800000000")
                        .valueType(ValueType.TEXT)
                        .build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRole()).isEqualTo("项目最高决策人");
        assertThat(rows.get(0).getName()).isEqualTo("张总");
        assertThat(rows.get(0).getContactInfo()).isEqualTo("13800000000");
    }

    @Test
    void toCustomerInfoRows_returnsMultipleRowsForMultipleRoles() {
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("PROJECT_HIGHEST_DECISION_MAKER")
                        .infoKey("NAME")
                        .cellValue("张总")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("ELECTRONICS_COMPANY_GENERAL_MANAGER")
                        .infoKey("NAME")
                        .cellValue("李经理")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("ELECTRONICS_COMPANY_GENERAL_MANAGER")
                        .infoKey("POSITION")
                        .cellValue("5")
                        .valueType(ValueType.ENUM14)
                        .build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(CustomerInfoRow::getName).containsExactlyInAnyOrder("张总", "李经理");
    }

    @Test
    void toCustomerInfoRows_mapsAllKnownInfoKeys() {
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("NAME")
                        .cellValue("专家A")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("CONTACT_INFO")
                        .cellValue("expert@test.com")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("POSITION")
                        .cellValue("12")
                        .valueType(ValueType.ENUM14)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("XIYU_CONTACT")
                        .cellValue("小王")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("CONTACTED")
                        .cellValue("true")
                        .valueType(ValueType.DROPDOWN)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("CONTACT_METHOD")
                        .cellValue("2")
                        .valueType(ValueType.ENUM7)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("INFO_TENDENCY_BASIS")
                        .cellValue("明确支持")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("GUIDED_BID")
                        .cellValue("true")
                        .valueType(ValueType.DROPDOWN)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("CAN_GET_KEY_INFO")
                        .cellValue("true")
                        .valueType(ValueType.DROPDOWN)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("CAN_REMOVE_ADVERSE")
                        .cellValue("false")
                        .valueType(ValueType.DROPDOWN)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("CAN_SYNC_EVAL")
                        .cellValue("true")
                        .valueType(ValueType.DROPDOWN)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("TENDENCY")
                        .cellValue("1")
                        .valueType(ValueType.DROPDOWN)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("INFO_CLEAR_WINNER_BID")
                        .cellValue("true")
                        .valueType(ValueType.SWITCH)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("INFO_WIN_RATE_IMPACT")
                        .cellValue("3")
                        .valueType(ValueType.DROPDOWN6)
                        .build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(1);
        CustomerInfoRow row = rows.get(0);
        assertThat(row.getRole()).isEqualTo("专家1");
        assertThat(row.getName()).isEqualTo("专家A");
        assertThat(row.getContactInfo()).isEqualTo("expert@test.com");
        assertThat(row.getPosition()).isEqualTo("12");
        assertThat(row.getXiyuContact()).isEqualTo("小王");
        assertThat(row.getReached()).isEqualTo("true");
        assertThat(row.getReachMethod()).isEqualTo("2");
        assertThat(row.getPreferenceBasis()).isEqualTo("明确支持");
        assertThat(row.getGuideBid()).isEqualTo("true");
        assertThat(row.getCanGetKeyInfo()).isEqualTo("true");
        assertThat(row.getCanRemoveAdverse()).isEqualTo("false");
        assertThat(row.getCanSyncEval()).isEqualTo("true");
        assertThat(row.getPreference()).isEqualTo("1");
        assertThat(row.getCanConfirmWin()).isEqualTo("true");
        assertThat(row.getWinRateImpact()).isEqualTo("3");
    }

    @Test
    void toCustomerInfoRows_ignoresUnknownInfoKeys() {
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("UNKNOWN_KEY")
                        .cellValue("xxx")
                        .valueType(ValueType.TEXT)
                        .build(),
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("NAME")
                        .cellValue("专家A")
                        .valueType(ValueType.TEXT)
                        .build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getName()).isEqualTo("专家A");
    }

    @Test
    void toCustomerInfoRows_evaluationBasisMapsToPreferenceBasis() {
        List<TenderEvaluationCustomerInfo> infos = List.of(
                TenderEvaluationCustomerInfo.builder()
                        .roleKey("EXPERT_1")
                        .infoKey("EVALUATION_BASIS")
                        .cellValue("CRM回填依据")
                        .valueType(ValueType.TEXT)
                        .build());

        List<CustomerInfoRow> rows = EvaluationToInitiationMapper.toCustomerInfoRows(infos);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPreferenceBasis()).isEqualTo("CRM回填依据");
    }
}
