package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
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
import static org.mockito.Mockito.mock;

/**
 * {@link TenderIntegrationCommandService#pushTender} 评估数据落库验证。
 */
@DataJpaTest
@ActiveProfiles("test")
class TenderIntegrationServicePushEvaluationTest {

    @Autowired private TenderRepository tenderRepository;
    @Autowired private TenderEvaluationRepository tenderEvaluationRepository;
    @Autowired private TenderEvaluationCustomerInfoRepository customerInfoRepository;

    private TenderIntegrationCommandService commandService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository,
                mock(TenderEvaluationSubmissionMapper.class));
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                mock(TenderMapper.class),
                evaluationMapper);
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository,
                evaluationMapper);
        commandService = new TenderIntegrationCommandService(
                tenderRepository,
                mock(TenderAttachmentRepository.class),
                mock(CrmTenderLinkService.class),
                mapper,
                evaluationService);
    }

    private TenderPushRequest.EvaluationUpdate buildEval(String roleKey, String infoKey, String value) {
        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", roleKey);
        roleData.put(infoKey, value);
        return TenderPushRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of(roleData))
                .build();
    }

    private TenderPushRequest buildPushRequest(String sourceSystem, String sourceId,
                                               boolean forceUpdate,
                                               TenderPushRequest.EvaluationUpdate evaluation) {
        return TenderPushRequest.builder()
                .sourceSystem(sourceSystem)
                .sourceId(sourceId)
                .title("测试标讯")
                .forceUpdate(forceUpdate)
                .evaluation(evaluation)
                .build();
    }

    @Test
    @DisplayName("pushTender 创建时携带 evaluationCustomerInfos 应保存客户信息")
    void pushTender_createWithEvaluation_shouldSaveCustomerInfos() {
        TenderPushRequest request = buildPushRequest("CRM", "OPP-001", false,
                buildEval("DECISION_MAKER", "attitude", "支持"));

        TenderPushResponse response = commandService.pushTender(request, null);

        assertThat(response.getStatus()).isEqualTo("CREATED");
        Long tenderId = response.getTenderId();
        Tender tender = tenderRepository.findById(tenderId).orElseThrow();
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        assertThat(eval.getEvaluationStatus()).isEqualTo(TenderEvaluation.EvaluationStatus.DRAFT);
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoleKey()).isEqualTo("DECISION_MAKER");
        assertThat(rows.get(0).getInfoKey()).isEqualTo("attitude");
        assertThat(rows.get(0).getCellValue()).isEqualTo("支持");
    }

    @Test
    @DisplayName("pushTender forceUpdate 已存在标讯时应覆盖保存 evaluationCustomerInfos")
    void pushTender_forceUpdateExistingWithEvaluation_shouldSaveCustomerInfos() {
        Tender first = new Tender();
        first.setTitle("原始标讯");
        first.setExternalId("CRM:OPP-002");
        first.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        first.setSourceType(Tender.SourceType.EXTERNAL_PLATFORM);
        tenderRepository.save(first);

        TenderPushRequest request = buildPushRequest("CRM", "OPP-002", true,
                buildEval("INFLUENCER", "position", "总监"));

        TenderPushResponse response = commandService.pushTender(request, null);

        assertThat(response.getStatus()).isEqualTo("UPDATED");
        Long tenderId = response.getTenderId();
        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoleKey()).isEqualTo("INFLUENCER");
        assertThat(rows.get(0).getInfoKey()).isEqualTo("position");
        assertThat(rows.get(0).getCellValue()).isEqualTo("总监");
    }

    @Test
    @DisplayName("pushTender 应将 CRM 字段名 CONTACT/EVALUATION_BASIS 标准化为 CONTACT_INFO/INFO_TENDENCY_BASIS")
    void pushTender_crmLegacyInfoKeys_shouldBeNormalizedBeforeSave() {
        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        roleData.put("NAME", "张三");
        roleData.put("CONTACT", "13800138000");
        roleData.put("EVALUATION_BASIS", "长期合作");
        TenderPushRequest.EvaluationUpdate evaluation = TenderPushRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of(roleData))
                .build();
        TenderPushRequest request = buildPushRequest("CRM", "OPP-003", false, evaluation);

        TenderPushResponse response = commandService.pushTender(request, null);

        assertThat(response.getStatus()).isEqualTo("CREATED");
        Long tenderId = response.getTenderId();
        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(3);
        assertThat(rows).anyMatch(r -> "PROJECT_HIGHEST_DECISION_MAKER".equals(r.getRoleKey())
                && "NAME".equals(r.getInfoKey()) && "张三".equals(r.getCellValue()));
        assertThat(rows).anyMatch(r -> "PROJECT_HIGHEST_DECISION_MAKER".equals(r.getRoleKey())
                && "CONTACT_INFO".equals(r.getInfoKey()) && "13800138000".equals(r.getCellValue()));
        assertThat(rows).anyMatch(r -> "PROJECT_HIGHEST_DECISION_MAKER".equals(r.getRoleKey())
                && "INFO_TENDENCY_BASIS".equals(r.getInfoKey()) && "长期合作".equals(r.getCellValue()));
    }
}
