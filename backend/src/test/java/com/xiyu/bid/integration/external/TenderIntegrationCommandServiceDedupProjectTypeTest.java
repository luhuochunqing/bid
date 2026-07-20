package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderAssignmentNotifier;
import com.xiyu.bid.tender.service.TenderAutoAssignmentService;
import com.xiyu.bid.tender.service.TenderAuditService;
import com.xiyu.bid.tender.service.TenderCrmOccupancyChecker;
import com.xiyu.bid.tender.service.TenderDeduplicationService;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 第三方平台推送路径去重维度测试。
 *
 * <p>验证 {@code TenderIntegrationCommandService.pushTender} 在创建新标讯前，
 * 通过 {@link TenderDeduplicationService} 执行四字段去重判定（招标主体+项目类型+报名截止+开标时间）。
 *
 * <p>根因：PR !2076 标讯去重新增 projectType 维度后，人工录入路径已走新 Policy，
 * 但第三方推送路径仍走旧的三字段 Repository 派生查询，导致 projectType 维度失效。
 * 本次修复让推送路径也复用 TenderDeduplicationService，确保全链路去重维度一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("第三方推送去重 - projectType 维度")
class TenderIntegrationCommandServiceDedupProjectTypeTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderAttachmentRepository attachmentRepository;
    @Mock private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock private TenderEvaluationSubmissionMapper submissionMapper;
    @Mock private CrmTenderLinkService crmTenderLinkService;
    @Mock private ProjectDocumentRepository projectDocumentRepository;
    @Mock private TenderAutoAssignmentService autoAssignmentService;
    @Mock private TenderAssignmentNotifier assignmentNotifier;
    @Mock private TenderAuditService tenderAuditService;
    @Mock private TenderCrmOccupancyChecker crmOccupancyChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ProjectManagerIdResolver projectManagerIdResolver;
    @Mock private ProjectManagerDepartmentEnricher departmentEnricher;

    private TenderIntegrationCommandService commandService;

    private static final String PURCHASER = "海德鲁铝型材（上海）有限公司";
    private static final LocalDateTime REG_DEADLINE = LocalDateTime.of(2026, 7, 15, 18, 20);
    private static final LocalDateTime BID_OPEN = LocalDateTime.of(2026, 7, 18, 18, 20);

    @BeforeEach
    void setUp() {
        TenderEvaluationIntegrationMapper evaluationMapper = new TenderEvaluationIntegrationMapper(
                tenderEvaluationRepository, submissionMapper);
        TenderIntegrationMapper mapper = new TenderIntegrationMapper(
                tenderMapper, evaluationMapper, projectManagerIdResolver);
        TenderEvaluationIntegrationService evaluationService = new TenderEvaluationIntegrationService(
                tenderEvaluationRepository, evaluationMapper, projectDocumentRepository);
        TenderIntegrationResolver helper = new TenderIntegrationResolver(tenderRepository);
        TenderIntegrationCommandSupport support = new TenderIntegrationCommandSupport(
                crmTenderLinkService,
                autoAssignmentService,
                assignmentNotifier,
                eventPublisher,
                tenderRepository,
                projectManagerIdResolver,
                departmentEnricher,
                userRepository);
        commandService = new TenderIntegrationCommandService(
                tenderRepository, attachmentRepository, crmTenderLinkService, mapper, evaluationService, helper, support,
                eventPublisher, tenderAuditService, userRepository, crmOccupancyChecker,
                new com.xiyu.bid.webhook.application.OperatorUsernameResolver(userRepository),
                new TenderDeduplicationService(tenderRepository),
                projectManagerIdResolver);

        // 默认：externalId 不存在（走创建新标讯路径）
        when(tenderRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        TenderDTO stubDto = TenderDTO.builder().build();
        when(tenderMapper.toDTO(any(Tender.class))).thenReturn(stubDto);
        when(tenderMapper.buildContacts(any(Tender.class))).thenReturn(Collections.emptyList());
    }

    private Tender existingTender(String projectType) {
        Tender t = new Tender();
        t.setId(100L);
        t.setExternalId("crm:test-001");
        t.setTitle("已有标讯");
        t.setPurchaserName(PURCHASER);
        t.setProjectType(projectType);
        t.setRegistrationDeadline(REG_DEADLINE);
        t.setBidOpeningTime(BID_OPEN);
        t.setStatus(Tender.Status.TRACKING);
        return t;
    }

    private TenderPushRequest buildPushRequest(String projectType) {
        TenderPushRequest req = new TenderPushRequest();
        req.setSourceSystem("CRM");
        req.setSourceId("900");
        req.setTitle("测试推送");
        req.setCustomerName(PURCHASER);
        req.setProjectType(projectType);
        req.setRegistrationDeadline("2026-07-15T18:20:00");
        req.setBidOpeningTime("2026-07-18T18:20:00");
        return req;
    }

    @Test
    @DisplayName("projectType 相同 → 应被去重拦截")
    void pushTender_sameProjectType_shouldReject() {
        when(tenderRepository.findByPurchaserNameAllIgnoreCase(PURCHASER))
                .thenReturn(List.of(existingTender("综合")));

        TenderPushRequest req = buildPushRequest("综合");

        assertThatThrownBy(() -> commandService.pushTender(req, 1L))
                .isInstanceOf(TenderDuplicateException.class)
                .hasMessageContaining("投标管理系统该标讯已存在");
    }

    @Test
    @DisplayName("projectType 不同 → 应创建成功（不拦截）")
    void pushTender_differentProjectType_shouldCreate() {
        when(tenderRepository.findByPurchaserNameAllIgnoreCase(PURCHASER))
                .thenReturn(List.of(existingTender("综合")));

        TenderPushRequest req = buildPushRequest("办公");

        var response = commandService.pushTender(req, 1L);
        assertThat(response.getStatus()).isEqualTo("CREATED");
        assertThat(response.getMessage()).contains("创建成功");
    }

    @Test
    @DisplayName("projectType 为 null 且 DB 也为 null → 应被去重拦截")
    void pushTender_bothProjectTypeNull_shouldReject() {
        when(tenderRepository.findByPurchaserNameAllIgnoreCase(PURCHASER))
                .thenReturn(List.of(existingTender(null)));

        TenderPushRequest req = buildPushRequest(null);

        assertThatThrownBy(() -> commandService.pushTender(req, 1L))
                .isInstanceOf(TenderDuplicateException.class)
                .hasMessageContaining("投标管理系统该标讯已存在");
    }

    @Test
    @DisplayName("projectType 为 null，DB 有值 → 应创建成功")
    void pushTender_nullVsNonNullProjectType_shouldCreate() {
        when(tenderRepository.findByPurchaserNameAllIgnoreCase(PURCHASER))
                .thenReturn(List.of(existingTender("综合")));

        TenderPushRequest req = buildPushRequest(null);

        var response = commandService.pushTender(req, 1L);
        assertThat(response.getStatus()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("projectType 有值，DB 为 null → 应创建成功")
    void pushTender_nonNullVsNullProjectType_shouldCreate() {
        when(tenderRepository.findByPurchaserNameAllIgnoreCase(PURCHASER))
                .thenReturn(List.of(existingTender(null)));

        TenderPushRequest req = buildPushRequest("综合");

        var response = commandService.pushTender(req, 1L);
        assertThat(response.getStatus()).isEqualTo("CREATED");
    }
}
