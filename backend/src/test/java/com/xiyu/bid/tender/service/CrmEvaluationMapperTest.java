package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("CrmEvaluationMapper - CRM 对接人映射为客户信息 EAV")
class CrmEvaluationMapperTest {

    private CrmEvaluationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CrmEvaluationMapper(new ObjectMapper());
    }

    @Test
    @DisplayName("contactMethod 为空时不生成 CONTACT_METHOD 行，与前端空值过滤逻辑对齐")
    void mapContactsToCustomerInfos_blankContactMethod_skipsContactMethodRow() {
        ContactPersonInfoVO contact = new ContactPersonInfoVO(
                1L, "张三", "18688888888", null, "4", "王凯毅",
                true, "", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        List<EvaluationCustomerInfoDTO> rows = mapper.mapContactsToCustomerInfos(List.of(contact));

        assertThat(rows)
                .extracting(EvaluationCustomerInfoDTO::infoKey)
                .contains("NAME", "CONTACT_INFO", "POSITION", "XIYU_CONTACT", "CONTACTED")
                .doesNotContain("CONTACT_METHOD", "INFO_TENDENCY_BASIS", "TENDENCY", "INFO_WIN_RATE_IMPACT");
    }

    @Test
    @DisplayName("所有字段齐全时生成完整 14 行")
    void mapContactsToCustomerInfos_allFieldsPresent_generates14Rows() {
        ContactPersonInfoVO contact = new ContactPersonInfoVO(
                1L, "张三", "18688888888", "zhangsan@example.com", "4", "王凯毅",
                true, "1", "2", "依据",
                true, true, true, true, true,
                true, "3", null, null, null, null, null, null);

        List<EvaluationCustomerInfoDTO> rows = mapper.mapContactsToCustomerInfos(List.of(contact));

        assertThat(rows).hasSize(14);
        assertThat(rows)
                .extracting(EvaluationCustomerInfoDTO::infoKey, EvaluationCustomerInfoDTO::value, EvaluationCustomerInfoDTO::valueType)
                .contains(
                        tuple("NAME", "张三", "TEXT"),
                        tuple("CONTACT_INFO", "18688888888", "TEXT"),
                        tuple("POSITION", "4", "ENUM14"),
                        tuple("XIYU_CONTACT", "王凯毅", "TEXT"),
                        tuple("CONTACT_METHOD", "1", "ENUM7"),
                        tuple("INFO_TENDENCY_BASIS", "依据", "TEXT"),
                        tuple("CONTACTED", "是", "DROPDOWN"),
                        tuple("GUIDED_BID", "是", "DROPDOWN"),
                        tuple("CAN_GET_KEY_INFO", "是", "DROPDOWN"),
                        tuple("CAN_REMOVE_ADVERSE", "是", "DROPDOWN"),
                        tuple("CAN_SYNC_EVAL", "是", "DROPDOWN"),
                        tuple("TENDENCY", "2", "DROPDOWN"),
                        tuple("INFO_CLEAR_WINNER_BID", "true", "SWITCH"),
                        tuple("INFO_WIN_RATE_IMPACT", "3", "DROPDOWN6"));
    }

    @Test
    @DisplayName("INFO_CLEAR_WINNER_BID 为 false 时仍生成该行")
    void mapContactsToCustomerInfos_falseClearWinnerBid_keepsSwitchRow() {
        ContactPersonInfoVO contact = new ContactPersonInfoVO(
                1L, "张三", "18688888888", null, "4", "王凯毅",
                false, null, null, null,
                null, null, null, null, null,
                false, null, null, null, null, null, null, null);

        List<EvaluationCustomerInfoDTO> rows = mapper.mapContactsToCustomerInfos(List.of(contact));

        assertThat(rows)
                .extracting(EvaluationCustomerInfoDTO::infoKey, EvaluationCustomerInfoDTO::value)
                .contains(tuple("INFO_CLEAR_WINNER_BID", "false"));
    }

    @Test
    @DisplayName("CONTACT_INFO 取 phone，phone 为空时取 email，都为空则跳过")
    void mapContactsToCustomerInfos_contactInfoPriority() {
        ContactPersonInfoVO phoneOnly = new ContactPersonInfoVO(
                1L, "张三", "18688888888", null, "4", null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        ContactPersonInfoVO emailOnly = new ContactPersonInfoVO(
                2L, "李四", null, "lisi@example.com", "5", null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        ContactPersonInfoVO empty = new ContactPersonInfoVO(
                3L, "王五", null, null, "6", null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        assertThat(mapper.mapContactsToCustomerInfos(List.of(phoneOnly)))
                .extracting(EvaluationCustomerInfoDTO::infoKey, EvaluationCustomerInfoDTO::value)
                .contains(tuple("CONTACT_INFO", "18688888888"));

        assertThat(mapper.mapContactsToCustomerInfos(List.of(emailOnly)))
                .extracting(EvaluationCustomerInfoDTO::infoKey, EvaluationCustomerInfoDTO::value)
                .contains(tuple("CONTACT_INFO", "lisi@example.com"));

        assertThat(mapper.mapContactsToCustomerInfos(List.of(empty)))
                .extracting(EvaluationCustomerInfoDTO::infoKey)
                .doesNotContain("CONTACT_INFO");
    }

    @Test
    @DisplayName("多个对接人时各自独立生成行")
    void mapContactsToCustomerInfos_multipleContacts_mapsEachRole() {
        ContactPersonInfoVO c1 = new ContactPersonInfoVO(
                1L, "张三", "18688888888", null, "4", null,
                null, "2", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        ContactPersonInfoVO c2 = new ContactPersonInfoVO(
                2L, "李四", "18777777777", null, "5", null,
                null, "3", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);

        List<EvaluationCustomerInfoDTO> rows = mapper.mapContactsToCustomerInfos(List.of(c1, c2));

        assertThat(rows)
                .extracting(EvaluationCustomerInfoDTO::roleKey, EvaluationCustomerInfoDTO::infoKey, EvaluationCustomerInfoDTO::value)
                .contains(
                        tuple("ELECTRONICS_COMPANY_CHAIRMAN", "NAME", "张三"),
                        tuple("ELECTRONICS_COMPANY_CHAIRMAN", "CONTACT_METHOD", "2"),
                        tuple("ELECTRONICS_COMPANY_GENERAL_MANAGER", "NAME", "李四"),
                        tuple("ELECTRONICS_COMPANY_GENERAL_MANAGER", "CONTACT_METHOD", "3"));
    }
}
