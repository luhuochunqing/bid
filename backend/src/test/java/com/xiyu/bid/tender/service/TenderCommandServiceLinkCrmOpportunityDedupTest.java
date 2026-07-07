package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.crm.application.CrmTenderSubjectChecker;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-297: TenderCommandService.linkCrmOpportunity 与 TenderCrmOccupancyChecker 的协作测试。
 * <p>CO-501: 扩展测试两步校验（CRM 远程校验 + 本地一致性校验）+ purchaserId 落库。
 *
 * <p>职责边界：
 * - CrmTenderSubjectChecker / TenderSubjectConsistencyPolicy 自身的判定由各自单测覆盖。
 * - 本测试验证 service 端是否正确调用 checker、透传异常、落库 purchaserId。
 */
@ExtendWith(MockitoExtension.class)
class TenderCommandServiceLinkCrmOpportunityDedupTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderCommandAccessGuard commandAccessGuard;
    @Mock private TenderMapper tenderMapper;
    @Mock private TenderCrmOccupancyChecker crmOccupancyChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserRepository userRepository;
    @Mock private TenderAssignmentRecordRepository assignmentRecordRepository;
    @Mock private TenderAuditService tenderAuditService;
    @Mock private CrmTenderSubjectChecker crmTenderSubjectChecker;

    private TenderCommandService tenderCommandService;

    private Tender tenderA;
    private static final String CRM_OPP_X = "CRM-OPP-X";
    private static final String PURCHASER = "山东海化集团有限公司";
    private static final String USERNAME = "sales";

    @BeforeEach
    void setUp() {
        tenderA = Tender.builder()
                .id(100L)
                .title("标讯 A")
                .status(Tender.Status.TRACKING)
                .purchaserName(PURCHASER)
                .build();
        tenderCommandService = new TenderCommandService(
                null,                  // TenderDeduplicationService
                tenderRepository,
                null,                  // ProjectRepository
                tenderMapper,
                null,                  // TenderProjectAccessGuard
                commandAccessGuard,
                null,                  // TenderAutoAssignmentService
                eventPublisher,
                userRepository,
                null,                  // TenderAssignmentNotifier
                null,                  // TenderAttachmentRepository
                crmOccupancyChecker,
                null,                  // TenderEvaluationBackfillService
                null,                  // ProjectManagerIdResolver
                assignmentRecordRepository,
                tenderAuditService,
                crmTenderSubjectChecker);
    }

    @Test
    @DisplayName("CO-297 happy + CO-310 两步流程 + CO-501 两步校验通过：关联成功并落库 purchaserId")
    void linkCrmOpportunity_WhenBothChecksPass_ShouldSucceedAndSetPurchaserId() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        when(tenderRepository.save(tenderA)).thenReturn(tenderA);
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).username(USERNAME).fullName("Sales").build()));
        when(crmTenderSubjectChecker.check(eq(PURCHASER), eq(CRM_OPP_X), eq(USERNAME)))
                .thenReturn(CrmTenderSubjectChecker.CheckResult.passed(12345L));
        when(tenderMapper.toDTO(tenderA)).thenReturn(TenderDTO.builder().id(100L).build());
        // 用 req 重载，传 chanceGroupName=标讯的 purchaserName 让第二步本地校验通过
        com.xiyu.bid.tender.dto.TenderCrmLinkRequest req = com.xiyu.bid.tender.dto.TenderCrmLinkRequest.builder()
                .crmOpportunityId(CRM_OPP_X)
                .crmOpportunityName("商机 X")
                .chanceGroupName(PURCHASER)
                .chanceTenderSubject(PURCHASER)
                .build();

        TenderDTO result = tenderCommandService.linkCrmOpportunity(100L, req, 1L);

        assertThat(result).isNotNull();
        assertThat(tenderA.getCrmOpportunityId()).isEqualTo(CRM_OPP_X);
        // CO-464: purchaserId 已落库为 CRM 返回的招标主体 ID
        assertThat(tenderA.getPurchaserId()).isEqualTo(12345L);
        assertThat(tenderA.getStatus()).isEqualTo(Tender.Status.TRACKING);
        verify(crmOccupancyChecker).assertCrmOpportunityNotOccupied(100L, CRM_OPP_X);
        verify(assignmentRecordRepository).save(any(com.xiyu.bid.batch.entity.TenderAssignmentRecord.class));
    }

    @Test
    @DisplayName("CO-297 冲突：占位校验抛 409 → service 透传，crmOpportunityId 不被覆盖，不调 CRM subject 校验")
    void linkCrmOpportunity_WhenOccupancyCheckerThrows409_ShouldPropagateAndSkipSubjectCheck() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        doThrow(new BusinessException(409, "该 CRM 商机已被标讯 ID=200 关联，请先解除原关联"))
                .when(crmOccupancyChecker).assertCrmOpportunityNotOccupied(anyLong(), anyString());

        assertThatThrownBy(() -> tenderCommandService.linkCrmOpportunity(100L, CRM_OPP_X, "商机 X", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被标讯");

        assertThat(tenderA.getCrmOpportunityId()).isNull();
        verify(crmTenderSubjectChecker, never()).check(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("CO-501 第一步失败（NOT_IN_CRM）→ 抛 CO-501 原文文案，不落库")
    void linkCrmOpportunity_WhenCrmSubjectNotInCrm_ShouldThrowWithOriginalMessage() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).username(USERNAME).build()));
        when(crmTenderSubjectChecker.check(eq(PURCHASER), eq(CRM_OPP_X), eq(USERNAME)))
                .thenReturn(CrmTenderSubjectChecker.CheckResult.rejected(
                        CrmTenderSubjectChecker.ErrorCode.NOT_IN_CRM,
                        CrmTenderSubjectChecker.MSG_NOT_IN_CRM));

        assertThatThrownBy(() -> tenderCommandService.linkCrmOpportunity(100L, CRM_OPP_X, "商机 X", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("招标主体不存在CRM系统");

        assertThat(tenderA.getCrmOpportunityId()).isNull();
        assertThat(tenderA.getPurchaserId()).isNull();
    }

    @Test
    @DisplayName("CO-501 第一步失败（NOT_IN_GROUP）→ 抛 CO-501 原文文案")
    void linkCrmOpportunity_WhenCrmSubjectNotInGroup_ShouldThrowWithOriginalMessage() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).username(USERNAME).build()));
        when(crmTenderSubjectChecker.check(eq(PURCHASER), eq(CRM_OPP_X), eq(USERNAME)))
                .thenReturn(CrmTenderSubjectChecker.CheckResult.rejected(
                        CrmTenderSubjectChecker.ErrorCode.NOT_IN_GROUP,
                        CrmTenderSubjectChecker.MSG_NOT_IN_GROUP));

        assertThatThrownBy(() -> tenderCommandService.linkCrmOpportunity(100L, CRM_OPP_X, "商机 X", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于商机所属集团");
    }

    @Test
    @DisplayName("CO-501 第二步不一致（purchaserName 与 chance groupName/tenderSubject 都不同）→ 抛「招标主体不一致」")
    void linkCrmOpportunity_WhenLocalConsistencyFails_ShouldThrowInconsistent() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).username(USERNAME).build()));
        when(crmTenderSubjectChecker.check(eq(PURCHASER), eq(CRM_OPP_X), eq(USERNAME)))
                .thenReturn(CrmTenderSubjectChecker.CheckResult.passed(999L));
        com.xiyu.bid.tender.dto.TenderCrmLinkRequest req = com.xiyu.bid.tender.dto.TenderCrmLinkRequest.builder()
                .crmOpportunityId(CRM_OPP_X)
                .crmOpportunityName("商机 X")
                .chanceGroupName("中石化集团")          // 不匹配 PURCHASER
                .chanceTenderSubject("中石化子公司")    // 不匹配 PURCHASER
                .build();

        assertThatThrownBy(() -> tenderCommandService.linkCrmOpportunity(100L, req, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("招标主体不一致");

        assertThat(tenderA.getCrmOpportunityId()).isNull();
        assertThat(tenderA.getPurchaserId()).isNull();
    }

    @Test
    @DisplayName("CO-501 标讯缺少招标主体（purchaserName 为空）→ 抛 400「标讯缺少招标主体」")
    void linkCrmOpportunity_WhenPurchaserNameBlank_ShouldThrow400() {
        tenderA.setPurchaserName(null);
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));

        assertThatThrownBy(() -> tenderCommandService.linkCrmOpportunity(100L, CRM_OPP_X, "商机 X", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标讯缺少招标主体");

        verify(crmTenderSubjectChecker, never()).check(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("CO-501 第二步通过（purchaserName == chanceGroupName）→ 关联成功")
    void linkCrmOpportunity_WhenLocalConsistencyMatchesGroup_ShouldSucceed() {
        when(tenderRepository.findById(100L)).thenReturn(Optional.of(tenderA));
        when(tenderRepository.save(tenderA)).thenReturn(tenderA);
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).username(USERNAME).build()));
        when(crmTenderSubjectChecker.check(eq(PURCHASER), eq(CRM_OPP_X), eq(USERNAME)))
                .thenReturn(CrmTenderSubjectChecker.CheckResult.passed(777L));
        when(tenderMapper.toDTO(tenderA)).thenReturn(TenderDTO.builder().id(100L).build());
        // purchaserName == chanceGroupName → 第二步通过
        com.xiyu.bid.tender.dto.TenderCrmLinkRequest req = com.xiyu.bid.tender.dto.TenderCrmLinkRequest.builder()
                .crmOpportunityId(CRM_OPP_X)
                .crmOpportunityName("商机 X")
                .chanceGroupName(PURCHASER)
                .chanceTenderSubject("随便别的")
                .build();

        TenderDTO result = tenderCommandService.linkCrmOpportunity(100L, req, 1L);

        assertThat(result).isNotNull();
        assertThat(tenderA.getPurchaserId()).isEqualTo(777L);
    }
}
