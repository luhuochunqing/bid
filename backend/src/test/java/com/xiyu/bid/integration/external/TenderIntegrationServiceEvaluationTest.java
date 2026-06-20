package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * {@link TenderEvaluationIntegrationService#saveEvaluation} 的 JPA 集成测试。
 */
@DataJpaTest
@ActiveProfiles("test")
class TenderIntegrationServiceEvaluationTest {

    @Autowired private TenderRepository tenderRepository;
    @Autowired private TenderEvaluationRepository tenderEvaluationRepository;
    @Autowired private TenderEvaluationCustomerInfoRepository customerInfoRepository;

    private TenderEvaluationIntegrationService evaluationService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository,
                mock(TenderEvaluationSubmissionMapper.class));
        evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository,
                evaluationMapper);
    }

    private Long createTender() {
        Tender t = new Tender();
        t.setTitle("测试标讯");
        t.setExternalId("TEST:001");
        return tenderRepository.save(t).getId();
    }

    @Test
    @DisplayName("首次保存 customerInfos 应成功")
    void saveEvaluation_firstTime_shouldSucceed() {
        Long tenderId = createTender();

        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", "DECISION_MAKER");
        roleData.put("attitude", "支持");
        evaluationService.saveEvaluation(tenderId, null, List.of(roleData), null);

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoleKey()).isEqualTo("DECISION_MAKER");
        assertThat(rows.get(0).getInfoKey()).isEqualTo("attitude");
        assertThat(rows.get(0).getCellValue()).isEqualTo("支持");
    }

    @Test
    @DisplayName("二次更新相同 roleKey+infoKey 的 customerInfos 不应抛唯一约束异常")
    void saveEvaluation_secondUpdateWithSameRoleAndInfoKey_shouldNotThrow() {
        Long tenderId = createTender();

        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", "DECISION_MAKER");
        roleData.put("attitude", "支持");

        evaluationService.saveEvaluation(tenderId, null, List.of(roleData), null);

        Map<String, Object> updatedRoleData = new LinkedHashMap<>();
        updatedRoleData.put("roleKey", "DECISION_MAKER");
        updatedRoleData.put("attitude", "中立");

        assertThatCode(() -> evaluationService.saveEvaluation(tenderId, null, List.of(updatedRoleData), null))
                .doesNotThrowAnyException();

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCellValue()).isEqualTo("中立");
    }

    @Test
    @DisplayName("二次更新传入空数组 customerInfos 应清空旧数据")
    void saveEvaluation_secondUpdateWithEmptyArray_shouldClearOldRows() {
        Long tenderId = createTender();

        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", "DECISION_MAKER");
        roleData.put("attitude", "支持");

        evaluationService.saveEvaluation(tenderId, null, List.of(roleData), null);

        assertThatCode(() -> evaluationService.saveEvaluation(tenderId, null, List.of(), null))
                .doesNotThrowAnyException();

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("EAV 格式的客户信息应正确保存")
    void saveEvaluation_eavFormat_shouldSaveCorrectly() {
        Long tenderId = createTender();

        Map<String, Object> eavRow = new LinkedHashMap<>();
        eavRow.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        eavRow.put("infoKey", "CONTACT_INFO");
        eavRow.put("value", "13800138000");
        eavRow.put("valueType", "TEXT");

        evaluationService.saveEvaluation(tenderId, null, List.of(eavRow), null);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoleKey()).isEqualTo("PROJECT_HIGHEST_DECISION_MAKER");
        assertThat(rows.get(0).getInfoKey()).isEqualTo("CONTACT_INFO");
        assertThat(rows.get(0).getCellValue()).isEqualTo("13800138000");
        assertThat(rows.get(0).getValueType()).isEqualTo(TenderEvaluationCustomerInfo.ValueType.TEXT);
    }

    @Test
    @DisplayName("EAV 格式应将 CRM 旧字段名标准化")
    void saveEvaluation_eavLegacyInfoKeys_shouldBeNormalizedBeforeSave() {
        Long tenderId = createTender();

        Map<String, Object> contactRow = new LinkedHashMap<>();
        contactRow.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        contactRow.put("infoKey", "CONTACT");
        contactRow.put("value", "13800138000");
        contactRow.put("valueType", "TEXT");

        evaluationService.saveEvaluation(tenderId, null, List.of(contactRow), null);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getInfoKey()).isEqualTo("CONTACT_INFO");
    }

    @Test
    @DisplayName("EAV 格式应支持 valueType 为空时默认 TEXT")
    void saveEvaluation_eavFormatWithNullValueType_shouldDefaultToText() {
        Long tenderId = createTender();

        Map<String, Object> eavRow = new LinkedHashMap<>();
        eavRow.put("roleKey", "DECISION_MAKER");
        eavRow.put("infoKey", "NAME");
        eavRow.put("value", "张三");

        evaluationService.saveEvaluation(tenderId, null, List.of(eavRow), null);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getValueType()).isEqualTo(TenderEvaluationCustomerInfo.ValueType.TEXT);
    }

    @Test
    @DisplayName("Flat 格式 14 个客户信息字段应按 infoKey 保存标准 valueType")
    void saveEvaluation_flatFormatWith14CustomerInfoFields_shouldSaveExpectedValueTypes() {
        Long tenderId = createTender();

        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        roleData.put("NAME", "张三");
        roleData.put("CONTACT_INFO", "13800138000");
        roleData.put("POSITION", 1);
        roleData.put("XIYU_CONTACT", "李经理");
        roleData.put("CONTACT_METHOD", 3);
        roleData.put("INFO_TENDENCY_BASIS", "客户明确支持");
        roleData.put("CONTACTED", true);
        roleData.put("GUIDED_BID", false);
        roleData.put("CAN_GET_KEY_INFO", true);
        roleData.put("CAN_REMOVE_ADVERSE", false);
        roleData.put("CAN_SYNC_EVAL", true);
        roleData.put("TENDENCY", 2);
        roleData.put("INFO_CLEAR_WINNER_BID", true);
        roleData.put("INFO_WIN_RATE_IMPACT", 4);

        evaluationService.saveEvaluation(tenderId, null, List.of(roleData), null);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(14);
        assertRow(rows, "CONTACT_INFO", "13800138000", TenderEvaluationCustomerInfo.ValueType.TEXT);
        assertRow(rows, "POSITION", "1", TenderEvaluationCustomerInfo.ValueType.ENUM14);
        assertRow(rows, "CONTACT_METHOD", "3", TenderEvaluationCustomerInfo.ValueType.ENUM7);
        assertRow(rows, "CONTACTED", "true", TenderEvaluationCustomerInfo.ValueType.DROPDOWN);
        assertRow(rows, "GUIDED_BID", "false", TenderEvaluationCustomerInfo.ValueType.DROPDOWN);
        assertRow(rows, "TENDENCY", "2", TenderEvaluationCustomerInfo.ValueType.DROPDOWN);
        assertRow(rows, "INFO_CLEAR_WINNER_BID", "true", TenderEvaluationCustomerInfo.ValueType.SWITCH);
        assertRow(rows, "INFO_WIN_RATE_IMPACT", "4", TenderEvaluationCustomerInfo.ValueType.DROPDOWN6);
    }

    @Test
    @DisplayName("EAV 格式 valueType 缺失或错误时应按 infoKey 纠正")
    void saveEvaluation_eavFormatWithMissingOrWrongValueType_shouldUseExpectedType() {
        Long tenderId = createTender();

        Map<String, Object> position = new LinkedHashMap<>();
        position.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        position.put("infoKey", "POSITION");
        position.put("value", "1");
        position.put("valueType", "TEXT");

        Map<String, Object> contactMethod = new LinkedHashMap<>();
        contactMethod.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        contactMethod.put("infoKey", "CONTACT_METHOD");
        contactMethod.put("value", "3");

        Map<String, Object> winRateImpact = new LinkedHashMap<>();
        winRateImpact.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        winRateImpact.put("infoKey", "INFO_WIN_RATE_IMPACT");
        winRateImpact.put("value", "4");
        winRateImpact.put("valueType", 1);

        evaluationService.saveEvaluation(tenderId, null, List.of(position, contactMethod, winRateImpact), null);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(3);
        assertRow(rows, "POSITION", "1", TenderEvaluationCustomerInfo.ValueType.ENUM14);
        assertRow(rows, "CONTACT_METHOD", "3", TenderEvaluationCustomerInfo.ValueType.ENUM7);
        assertRow(rows, "INFO_WIN_RATE_IMPACT", "4", TenderEvaluationCustomerInfo.ValueType.DROPDOWN6);
    }

    @Test
    @DisplayName("EAV 格式 roleKey 为空时应生成外部角色并保留客户信息")
    void saveEvaluation_eavFormatWithoutRoleKey_shouldGenerateExternalRole() {
        Long tenderId = createTender();

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("infoKey", "NAME");
        name.put("value", "张三");
        name.put("valueType", "TEXT");

        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("roleKey", "");
        contact.put("infoKey", "CONTACT_INFO");
        contact.put("value", "18888888888");
        contact.put("valueType", "TEXT");

        evaluationService.saveEvaluation(tenderId, null, List.of(name, contact), null);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getRoleKey()).isEqualTo("EXTERNAL_ROLE_1");
            assertThat(row.getInfoKey()).isEqualTo("NAME");
            assertThat(row.getCellValue()).isEqualTo("张三");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getRoleKey()).isEqualTo("EXTERNAL_ROLE_2");
            assertThat(row.getInfoKey()).isEqualTo("CONTACT_INFO");
            assertThat(row.getCellValue()).isEqualTo("18888888888");
        });
    }

    private void assertRow(List<TenderEvaluationCustomerInfo> rows, String infoKey, String value,
                           TenderEvaluationCustomerInfo.ValueType valueType) {
        TenderEvaluationCustomerInfo row = rows.stream()
                .filter(r -> infoKey.equals(r.getInfoKey()))
                .findFirst()
                .orElseThrow();
        assertThat(row.getCellValue()).isEqualTo(value);
        assertThat(row.getValueType()).isEqualTo(valueType);
    }
}
