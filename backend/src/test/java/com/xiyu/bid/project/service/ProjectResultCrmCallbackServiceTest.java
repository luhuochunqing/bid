package com.xiyu.bid.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.application.CrmChanceService;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectResultCrmCallbackService 单元测试。
 * <p>覆盖修复点：
 * <ul>
 *   <li>载荷符合 BidInfoSyncDTO 契约（仅 name/code/status/statusEditor/statusEditTime/feedback 6 字段）。</li>
 *   <li>code ← tender.crmOpportunityId（商机编号），不可退回 externalId 的 sourceId 部分（lessons §4）。</li>
 *   <li>status ← CRM projectStatus 数字枚举（WON→2 LOST→3 FAILED→4 ABANDONED→6，lessons §5）。</li>
 *   <li>competitors 合入 feedback JSON（契约无独立 competitors 字段）。</li>
 *   <li>无商机关联 / 无 tenderId → 跳过，不调 CRM。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectResultCrmCallbackService — bidInfoSync contract + status mapping")
class ProjectResultCrmCallbackServiceTest {

    private static final Long PROJECT_ID = 9001L;
    private static final Long TENDER_ID = 254L;
    private static final Long USER_ID = 493L;
    private static final Long RESULT_ID = 7700L;
    private static final String CRM_CODE = "CC20260610180";
    private static final String CRM_NAME = "海化集团年度MRO采购";

    @Mock private ProjectRepository projectRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private CrmChanceService crmChanceService;

    private ProjectResultCrmCallbackService service() {
        return new ProjectResultCrmCallbackService(
                projectRepository, tenderRepository, userRepository, crmChanceService);
    }

    private void mockProjectTender() {
        Project project = new Project();
        project.setTenderId(TENDER_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        tender.setCrmOpportunityId(CRM_CODE);
        tender.setCrmOpportunityName(CRM_NAME);
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
    }

    private void mockUser(String fullName) {
        User user = new User();
        user.setFullName(fullName);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private BidInfoInnerDTO captureSingle(BidResultType resultType,
                                          List<ResultRegistrationRequest.CompetitorRow> competitors) {
        service().notifyResultConfirmed(PROJECT_ID, resultType, competitors, USER_ID, RESULT_ID);
        ArgumentCaptor<BidInfoSyncDTO> captor = ArgumentCaptor.forClass(BidInfoSyncDTO.class);
        verify(crmChanceService).bidInfoSync(captor.capture());
        assertThat(captor.getValue().bidInfoList()).hasSize(1);
        return captor.getValue().bidInfoList().get(0);
    }

    @Test
    @DisplayName("WON -> status=2, code/name 取自 crmOpportunityId/Name（非 externalId 的 sourceId）")
    void won_status2_codeFromCrmOpportunityId() {
        mockProjectTender();
        mockUser("张三");

        BidInfoInnerDTO inner = captureSingle(BidResultType.WON, List.of());

        assertThat(inner.status()).isEqualTo(2);
        assertThat(inner.code()).isEqualTo(CRM_CODE);
        assertThat(inner.name()).isEqualTo(CRM_NAME);
        assertThat(inner.statusEditor()).isEqualTo("张三");
    }

    @Test
    @DisplayName("LOST -> status=3")
    void lost_status3() {
        mockProjectTender();
        mockUser("李四");
        assertThat(captureSingle(BidResultType.LOST, List.of()).status()).isEqualTo(3);
    }

    @Test
    @DisplayName("FAILED -> status=4（流标）")
    void failed_status4() {
        mockProjectTender();
        mockUser("王五");
        assertThat(captureSingle(BidResultType.FAILED, List.of()).status()).isEqualTo(4);
    }

    @Test
    @DisplayName("ABANDONED -> status=6（弃标，文档曾误写 1-弃标，实际 1=跟进中）")
    void abandoned_status6() {
        mockProjectTender();
        mockUser("赵六");
        assertThat(captureSingle(BidResultType.ABANDONED, List.of()).status()).isEqualTo(6);
    }

    @Test
    @DisplayName("tender 无 crmOpportunityId -> code=\"\" name=\"\"，仍调用 CRM（不报错）")
    void noCrmOpportunity_emptyCodeStillCalls() {
        Project project = new Project();
        project.setTenderId(TENDER_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        Tender tender = new Tender();
        tender.setId(TENDER_ID);
        // crmOpportunityId/Name 保持 null
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        mockUser("张三");

        BidInfoInnerDTO inner = captureSingle(BidResultType.WON, List.of());

        assertThat(inner.code()).isEmpty();
        assertThat(inner.name()).isEmpty();
        assertThat(inner.status()).isEqualTo(2);
    }

    @Test
    @DisplayName("projectId 无 tenderId -> 跳过，不调 CRM")
    void noTenderId_skipsCrm() {
        Project project = new Project();
        // tenderId 保持 null
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        service().notifyResultConfirmed(PROJECT_ID, BidResultType.WON, List.of(), USER_ID, RESULT_ID);

        verify(crmChanceService, never()).bidInfoSync(any());
    }

    @Test
    @DisplayName("competitors 合入 feedback JSON：vendor=名称拼接、paymentTerm/remark/operateTime 齐全")
    void competitorsMergedIntoFeedback() throws Exception {
        mockProjectTender();
        mockUser("张三");
        var competitors = List.of(
                new ResultRegistrationRequest.CompetitorRow("西域", "9.5折", "月结30天", "价格有优势"),
                new ResultRegistrationRequest.CompetitorRow("震坤行", "9折", "货到付款", "交期快"));

        BidInfoInnerDTO inner = captureSingle(BidResultType.LOST, competitors);

        JsonNode fb = new ObjectMapper().readTree(inner.feedback());
        assertThat(fb.get("vendor").asText()).contains("西域", "震坤行");
        assertThat(fb.get("paymentTerm").asText()).contains("月结30天", "货到付款");
        assertThat(fb.get("remark").asText()).contains("价格有优势", "交期快");
        assertThat(fb.get("reason").asText()).contains("价格有优势"); // reason 优先取 notes
        assertThat(fb.get("operator").asText()).isEqualTo("张三");
        assertThat(fb.get("operateTime").asText()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("statusEditTime 格式 yyyy-MM-dd HH:mm:ss")
    void statusEditTime_format() {
        mockProjectTender();
        mockUser("张三");

        BidInfoInnerDTO inner = captureSingle(BidResultType.WON, List.of());

        assertThat(inner.statusEditTime()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("CRM 调用抛异常 -> 仅吞掉，不向上抛（不影响主流程）")
    void crmException_swallowed() {
        mockProjectTender();
        mockUser("张三");
        when(crmChanceService.bidInfoSync(any()))
                .thenThrow(new RuntimeException("CRM 下游超时"));

        // 不应抛异常
        service().notifyResultConfirmed(PROJECT_ID, BidResultType.WON, List.of(), USER_ID, RESULT_ID);
    }
}
