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
                1L, "张三", "18688888888", null, null, "4", "王凯毅",
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
    @DisplayName("fixed_phone 为空时生成基础 14 行（不产 TEL 行）")
    void mapContactsToCustomerInfos_allFieldsPresent_generates14Rows() {
        ContactPersonInfoVO contact = new ContactPersonInfoVO(
                1L, "张三", "18688888888", null, "zhangsan@example.com", "4", "王凯毅",
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
                1L, "张三", "18688888888", null, null, "4", "王凯毅",
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
                1L, "张三", "18688888888", null, null, "4", null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        ContactPersonInfoVO emailOnly = new ContactPersonInfoVO(
                2L, "李四", null, null, "lisi@example.com", "5", null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        ContactPersonInfoVO empty = new ContactPersonInfoVO(
                3L, "王五", null, null, null, "6", null,
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
                1L, "张三", "18688888888", null, null, "4", null,
                null, "2", null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        ContactPersonInfoVO c2 = new ContactPersonInfoVO(
                2L, "李四", "18777777777", null, null, "5", null,
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

    @Test
    @DisplayName("fixed_phone 有值时产生 TEL 行（TEXT），该对接人映射 15 行")
    void mapContactsToCustomerInfos_fixedPhonePresent_generatesTelRow() {
        ContactPersonInfoVO contact = new ContactPersonInfoVO(
                1L, "张三", "18688888888", "010-12345678", "zhangsan@example.com", "4", "王凯毅",
                true, "1", "2", "依据",
                true, true, true, true, true,
                true, "3", null, null, null, null, null, null);

        List<EvaluationCustomerInfoDTO> rows = mapper.mapContactsToCustomerInfos(List.of(contact));

        // 14 个基础维度 + TEL = 15 行
        assertThat(rows).hasSize(15);
        assertThat(rows)
                .extracting(EvaluationCustomerInfoDTO::infoKey, EvaluationCustomerInfoDTO::value, EvaluationCustomerInfoDTO::valueType)
                .contains(tuple("TEL", "010-12345678", "TEXT"));
    }

    @Test
    @DisplayName("fixed_phone 为 null/空串/空白时不产生 TEL 行，维度数仍为 14")
    void mapContactsToCustomerInfos_fixedPhoneBlank_skipsTelRow() {
        ContactPersonInfoVO fixedPhoneNull = new ContactPersonInfoVO(
                1L, "张三", "18688888888", null, null, "4", "王凯毅",
                true, "1", "2", "依据",
                true, true, true, true, true,
                true, "3", null, null, null, null, null, null);
        ContactPersonInfoVO fixedPhoneBlank = new ContactPersonInfoVO(
                2L, "李四", "18777777777", "  ", null, "5", "王凯毅",
                true, "1", "2", "依据",
                true, true, true, true, true,
                true, "3", null, null, null, null, null, null);

        // CRM 未上线 fixed_phone（null）→ 不产生 TEL 行，维度数保持 14
        assertThat(mapper.mapContactsToCustomerInfos(List.of(fixedPhoneNull)))
                .hasSize(14)
                .extracting(EvaluationCustomerInfoDTO::infoKey)
                .doesNotContain("TEL");

        // 空串/空白同样不落行（addIfHasValue 语义，与前端 TEL: c.fixed_phone || '' 对齐）
        assertThat(mapper.mapContactsToCustomerInfos(List.of(fixedPhoneBlank)))
                .hasSize(14)
                .extracting(EvaluationCustomerInfoDTO::infoKey)
                .doesNotContain("TEL");
    }
}
