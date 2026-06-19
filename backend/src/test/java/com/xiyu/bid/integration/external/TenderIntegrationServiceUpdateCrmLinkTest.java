package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CO-271: 验证 updateByExternalId 传入 crmId 时自动关联商机的兜底逻辑。
 *
 * <p>测试要点：
 * <ol>
 *   <li>crmId 非空 + CRM 正常 → evaluationSource=CRM_PUSH, status=EVALUATED</li>
 *   <li>crmId 非空 + CRM 异常降级 → crmOpportunityId 用传入 crmId 兜底</li>
 *   <li>crmId 为空 → 不触发兜底逻辑</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenderIntegrationServiceUpdateCrmLinkTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderAttachmentRepository attachmentRepository;
    @Mock private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock private TenderEvaluationCustomerInfoRepository customerInfoRepository;
    @Mock private TenderEvaluationSubmissionMapper submissionMapper;
    @Mock private CrmTenderLinkService crmTenderLinkService;

    private TenderIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new TenderIntegrationService(
                tenderRepository, tenderMapper, attachmentRepository,
                tenderEvaluationRepository, customerInfoRepository,
                submissionMapper, crmTenderLinkService);
        // tenderRepository.save 直接返回传入的 tender
        when(tenderRepository.save(any(Tender.class))).thenAnswer(inv -> inv.getArgument(0));
        // tenderMapper.toDTO 返回最小 DTO，避免 NPE
        TenderDTO stubDto = TenderDTO.builder().build();
        when(tenderMapper.toDTO(any(Tender.class))).thenReturn(stubDto);
        when(tenderMapper.buildContacts(any(Tender.class))).thenReturn(Collections.emptyList());
    }

    private Tender createExistingTender() {
        Tender t = new Tender();
        t.setId(1L);
        t.setExternalId("crm:test-001");
        t.setTitle("测试标讯");
        t.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        return t;
    }

    @Test
    @DisplayName("CO-271: crmId 非空时 evaluationSource 和 status 应被正确设置")
    void updateByExternalId_withCrmId_setsEvaluationSourceAndStatus() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // 模拟 linkIfPresent 正常设置 crmOpportunityId
        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CHANCE_001");
            t.setCrmOpportunityName("商机A");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CHANCE_001"));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("CHANCE_001")
                .build();

        service.updateByExternalId("crm", "test-001", request);

        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CHANCE_001");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机A");
    }

    @Test
    @DisplayName("CO-271: CRM 接口异常降级时 crmOpportunityId 用传入 crmId 兜底")
    void updateByExternalId_crmServiceThrows_fallbackSetsCrmOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // 模拟 linkIfPresent 因 CRM 异常未设置任何字段（降级场景）
        org.mockito.Mockito.doNothing().when(crmTenderLinkService).linkIfPresent(any(Tender.class), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("CHANCE_002")
                .build();

        service.updateByExternalId("crm", "test-001", request);

        // 兜底逻辑应确保 crmOpportunityId 被设置
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CHANCE_002");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-271: crmId 为空时不触发兜底逻辑")
    void updateByExternalId_nullCrmId_noFallback() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .title("更新标题")
                .build();

        service.updateByExternalId("crm", "test-001", request);

        // crmId 为空，不应设置 evaluationSource 和 status
        assertThat(tender.getEvaluationSource()).isNull();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    // ── CO-276: crmOpportunityId 字段名兼容（CRM 文档字段名 vs 代码历史字段名 crmId）──────────

    @Test
    @DisplayName("CO-276: 仅传 crmOpportunityId（不传 crmId）应触发关联，等价于传 crmId")
    void updateByExternalId_onlyCrmOpportunityId_triggersLink() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // linkIfPresent 收到合并后的 crmOpportunityId 值时应正常设置商机字段
        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC20260619283");
            t.setCrmOpportunityName("测试商机");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC20260619283"));

        // CRM 文档字段名为 crmOpportunityId，不传 crmId
        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619283")
                .crmOpportunityName("测试商机")
                .build();

        service.updateByExternalId("crm", "test-001", request);

        // linkIfPresent 应被调用且收到正确的商机编号
        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC20260619283"));
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("CO-276: crmOpportunityId 与 crmId 同时传时优先用 crmOpportunityId")
    void updateByExternalId_bothPresent_prefersCrmOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        org.mockito.Mockito.doAnswer(inv -> {
            Tender t = inv.getArgument(0);
            t.setCrmOpportunityId("CC-PUBLIC");
            t.setStatus(Tender.Status.EVALUATED);
            return null;
        }).when(crmTenderLinkService).linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC-PUBLIC"));

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmId("CC-LEGACY")
                .crmOpportunityId("CC-PUBLIC")
                .build();

        service.updateByExternalId("crm", "test-001", request);

        // 应优先用 crmOpportunityId 的值，而非 crmId
        org.mockito.Mockito.verify(crmTenderLinkService)
                .linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC-PUBLIC"));
        org.mockito.Mockito.verify(crmTenderLinkService,
                org.mockito.Mockito.never())
                .linkIfPresent(any(Tender.class), org.mockito.ArgumentMatchers.eq("CC-LEGACY"));
    }

    @Test
    @DisplayName("CO-276: 仅传 crmOpportunityId + CRM 异常降级时用 crmOpportunityId 兜底落库")
    void updateByExternalId_onlyCrmOpportunityId_fallbackSetsOpportunityId() {
        Tender tender = createExistingTender();
        when(tenderRepository.findByExternalId("crm:test-001")).thenReturn(Optional.of(tender));

        // linkIfPresent 因 CRM 异常未设置任何字段
        org.mockito.Mockito.doNothing().when(crmTenderLinkService).linkIfPresent(any(Tender.class), any());

        TenderUpdateRequest request = TenderUpdateRequest.builder()
                .crmOpportunityId("CC20260619283")
                .crmOpportunityName("测试商机")
                .build();

        service.updateByExternalId("crm", "test-001", request);

        // 兜底逻辑应确保 crmOpportunityId 和 crmOpportunityName 被设置
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("测试商机");
        assertThat(tender.getEvaluationSource()).isEqualTo(Tender.EvaluationSource.CRM_PUSH);
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }
}
