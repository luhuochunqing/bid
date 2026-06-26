package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.service.TenderEvaluationDocumentService;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.service.TenderAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired private ProjectDocumentRepository projectDocumentRepository;

    private TenderIntegrationCommandService commandService;

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository,
                mock(TenderEvaluationSubmissionMapper.class));
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                mock(TenderMapper.class),
                evaluationMapper,
                mock(ProjectManagerIdResolver.class));
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository,
                evaluationMapper,
                projectDocumentRepository);
        TenderIntegrationResolver helper = new TenderIntegrationResolver(tenderRepository);
        TenderIntegrationCommandSupport support = new TenderIntegrationCommandSupport(
                mock(CrmTenderLinkService.class),
                mock(TenderAutoAssignmentService.class),
                mock(TenderAssignmentNotifier.class),
                mock(ApplicationEventPublisher.class),
                tenderRepository);
        commandService = new TenderIntegrationCommandService(
                tenderRepository,
                mock(TenderAttachmentRepository.class),
                mock(CrmTenderLinkService.class),
                mapper,
                evaluationService,
                helper,
                support,
                mock(ApplicationEventPublisher.class),
                mock(TenderAuditService.class),
                mock(UserRepository.class));
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

    @Test
    @DisplayName("CO-262: pushTender 携带 projectPlanGapFiles 应持久化到 project_documents 表")
    void pushTender_withProjectPlanGapFiles_shouldPersistGapFiles() {
        EvaluationBasicDTO.GapFileRef gapRef = new EvaluationBasicDTO.GapFileRef(
                "测试GAP附件.pdf", "https://example.com/gap.pdf");
        EvaluationBasicDTO basic = new EvaluationBasicDTO(
                null, null, null, null, null, null, null,
                "测试项目计划GAP", null, List.of(gapRef));
        TenderPushRequest.EvaluationUpdate evaluation = TenderPushRequest.EvaluationUpdate.builder()
                .evaluationBasic(basic)
                .build();
        TenderPushRequest request = buildPushRequest("CRM", "OPP-GAP-001", false, evaluation);

        TenderPushResponse response = commandService.pushTender(request, null);

        assertThat(response.getStatus()).isEqualTo("CREATED");
        Long tenderId = response.getTenderId();

        List<ProjectDocument> docs = projectDocumentRepository
                .findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(
                        TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP, tenderId);
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getName()).isEqualTo("测试GAP附件.pdf");
        assertThat(docs.get(0).getFileUrl()).isEqualTo("https://example.com/gap.pdf");
        assertThat(docs.get(0).getDocumentCategory()).isEqualTo(TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP);
    }

    @Test
    @DisplayName("CO-262: forceUpdate 推送空 projectPlanGapFiles 应清空已有 GAP 附件")
    void pushTender_forceUpdateWithEmptyGapFiles_shouldClearExistingGapFiles() {
        Tender first = new Tender();
        first.setTitle("原始标讯");
        first.setExternalId("CRM:OPP-GAP-002");
        first.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        first.setSourceType(Tender.SourceType.EXTERNAL_PLATFORM);
        tenderRepository.save(first);

        EvaluationBasicDTO.GapFileRef gapRef = new EvaluationBasicDTO.GapFileRef(
                "旧GAP附件.pdf", "https://example.com/old-gap.pdf");
        EvaluationBasicDTO basicWithFile = new EvaluationBasicDTO(
                null, null, null, null, null, null, null,
                "旧GAP", null, List.of(gapRef));
        TenderPushRequest.EvaluationUpdate firstEvaluation = TenderPushRequest.EvaluationUpdate.builder()
                .evaluationBasic(basicWithFile)
                .build();
        commandService.pushTender(buildPushRequest("CRM", "OPP-GAP-002", true, firstEvaluation), null);

        EvaluationBasicDTO basicEmpty = new EvaluationBasicDTO(
                null, null, null, null, null, null, null,
                "已清空", null, List.of());
        TenderPushRequest.EvaluationUpdate secondEvaluation = TenderPushRequest.EvaluationUpdate.builder()
                .evaluationBasic(basicEmpty)
                .build();
        TenderPushResponse response = commandService.pushTender(
                buildPushRequest("CRM", "OPP-GAP-002", true, secondEvaluation), null);

        assertThat(response.getStatus()).isEqualTo("UPDATED");
        Long tenderId = response.getTenderId();
        List<ProjectDocument> docs = projectDocumentRepository
                .findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc(
                        TenderEvaluationDocumentService.ENTITY_TYPE_EVALUATION_GAP, tenderId);
        assertThat(docs).isEmpty();
    }

    @Test
    @DisplayName("CO-265: 不同 sourceId 但招标主体、报名截止、开标时间重复时应拒绝创建")
    void pushTender_duplicateBusinessKeyWithDifferentSourceId_shouldReject() {
        Tender existing = new Tender();
        existing.setTitle("已有标讯");
        existing.setExternalId("CRM:EXISTING-001");
        existing.setPurchaserName("西域测试客户");
        existing.setRegistrationDeadline(LocalDateTime.of(2026, 6, 30, 10, 0));
        existing.setBidOpeningTime(LocalDateTime.of(2026, 7, 1, 9, 30));
        existing.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        existing.setSourceType(Tender.SourceType.EXTERNAL_PLATFORM);
        tenderRepository.save(existing);
        long countBefore = tenderRepository.count();

        TenderPushRequest request = buildPushRequest("CRM", "NEW-001", false, null);
        request.setCustomerName("西域测试客户");
        request.setRegistrationDeadline("2026-06-30T10:00:00");
        request.setBidOpeningTime("2026-07-01T09:30:00");

        assertThatThrownBy(() -> commandService.pushTender(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("投标管理系统该标讯已存在");
        assertThat(tenderRepository.count()).isEqualTo(countBefore);
        assertThat(tenderRepository.findByExternalId("CRM:NEW-001")).isEmpty();
    }

    @Test
    @DisplayName("CO-265: 招标主体相同但时间不同仍应正常创建")
    void pushTender_samePurchaserDifferentTime_shouldCreate() {
        Tender existing = new Tender();
        existing.setTitle("已有标讯");
        existing.setExternalId("CRM:EXISTING-002");
        existing.setPurchaserName("西域测试客户");
        existing.setRegistrationDeadline(LocalDateTime.of(2026, 6, 30, 10, 0));
        existing.setBidOpeningTime(LocalDateTime.of(2026, 7, 1, 9, 30));
        existing.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        existing.setSourceType(Tender.SourceType.EXTERNAL_PLATFORM);
        tenderRepository.save(existing);

        TenderPushRequest request = buildPushRequest("CRM", "NEW-002", false, null);
        request.setCustomerName("西域测试客户");
        request.setRegistrationDeadline("2026-06-30T10:00:00");
        request.setBidOpeningTime("2026-07-02T09:30:00");

        TenderPushResponse response = commandService.pushTender(request, null);

        assertThat(response.getStatus()).isEqualTo("CREATED");
        Tender created = tenderRepository.findByExternalId("CRM:NEW-002").orElseThrow();
        assertThat(created.getPurchaserName()).isEqualTo("西域测试客户");
    }
}
