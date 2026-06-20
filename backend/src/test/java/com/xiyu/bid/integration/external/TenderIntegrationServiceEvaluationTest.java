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
}
