package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.application.CrmChanceService;
import com.xiyu.bid.crm.application.CrmContactPersonService;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation.BidRecommendation;
import com.xiyu.bid.webhook.infrastructure.CrmOpportunityCodeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-526: 提交建议投标时同步 CRM 商机和对接人信息 — TDD 测试。
 * <p>验证 {@link TenderEvaluationCrmSyncService} 在 submit 时拉取 CRM 最新数据覆盖 basic + customerInfos，
 * 保留用户填写的 bidRecommendation + evaluationRecommendation，并支持降级。
 */
@ExtendWith(MockitoExtension.class)
class TenderEvaluationCrmSyncServiceTest {

    @Mock
    private CrmChanceService crmChanceService;
    @Mock
    private CrmContactPersonService crmContactPersonService;
    @Mock
    private CrmOpportunityCodeResolver crmOpportunityCodeResolver;

    private TenderEvaluationCrmSyncService syncService;

    private static final String USERNAME = "sales1";
    private static final String CC_CODE = "CC20260610180";
    private static final Long CHANCE_ID = 20942L;

    @BeforeEach
    void setUp() {
        syncService = new TenderEvaluationCrmSyncService(
                crmChanceService, crmContactPersonService, crmOpportunityCodeResolver,
                new CrmEvaluationMapper(new ObjectMapper()));
    }

    // ---------- Test 1: crmOpportunityId 为 null → 返回原 req，不调 CRM ----------

    @Test
    @DisplayName("tender.crmOpportunityId 为 null 时返回原 req，不调用任何 CRM 服务")
    void testSyncFromCrm_nullCrmOpportunityId_returnsOriginalReq() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId(null);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        // when
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then
        assertThat(result).isSameAs(userReq);
        verify(crmChanceService, never()).pageList(any(), anyString());
        verify(crmContactPersonService, never()).pageList(anyLong(), anyString());
    }

    // ---------- Test 2: crmOpportunityId 为空字符串 → 返回原 req，不调 CRM ----------

    @Test
    @DisplayName("tender.crmOpportunityId 为空字符串时返回原 req，不调用任何 CRM 服务")
    void testSyncFromCrm_blankCrmOpportunityId_returnsOriginalReq() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId("   ");
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        // when
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then
        assertThat(result).isSameAs(userReq);
        verify(crmChanceService, never()).pageList(any(), anyString());
    }

    // ---------- Test 3: CRM 商机 + 对接人均查询成功 → 返回新 req，basic 和 customerInfos 来自 CRM ----------

    @Test
    @DisplayName("CRM 商机和对接人查询均成功时返回新 req，basic 来自 CRM，customerInfos 来自 CRM，保留 bidRecommendation 和 recommendation")
    void testSyncFromCrm_bothCrmQueriesSucceed_returnsNewReqWithCrmData() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId(CC_CODE);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        when(crmOpportunityCodeResolver.resolve(CC_CODE)).thenReturn(CC_CODE);
        when(crmChanceService.findByCode(CC_CODE, USERNAME)).thenReturn(buildCrmChance());
        when(crmContactPersonService.pageList(CHANCE_ID, USERNAME)).thenReturn(buildContactPersons());

        // when
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then
        assertThat(result).isNotSameAs(userReq);
        // 保留用户填写的投标建议
        assertThat(result.bidRecommendation()).isEqualTo(BidRecommendation.RECOMMEND);
        assertThat(result.evaluationRecommendation()).isEqualTo(userReq.evaluationRecommendation());
        // basic 来自 CRM
        EvaluationBasicDTO basic = result.evaluationBasic();
        assertThat(basic).isNotNull();
        assertThat(basic.riskAssessment()).isEqualTo("风险较高");
        assertThat(basic.unfavorableItems()).isEqualTo("技术参数不匹配");
        assertThat(basic.plannedShortlistedCount()).isEqualTo(5);
        assertThat(basic.mroOfficeFlowAmount()).isEqualByComparingTo(new BigDecimal("150.5"));
        assertThat(basic.contingencyPlan()).isEqualTo("是");
        assertThat(basic.processKnowledge()).isEqualTo("了解流程");
        assertThat(basic.supportNotes()).isEqualTo("重要备注");
        assertThat(basic.projectPlanGap()).isEqualTo("存在GAP");
        assertThat(basic.customerRevenue()).isEqualByComparingTo(new BigDecimal("800"));
        // customerInfos 来自 CRM：每个对接人 14 行
        List<EvaluationCustomerInfoDTO> infos = result.evaluationCustomerInfos();
        assertThat(infos).isNotNull();
        assertThat(infos).hasSize(14); // 1 个对接人 × 14 个维度
        // 验证 roleKey 映射正确（position=1 → PROJECT_HIGHEST_DECISION_MAKER）
        assertThat(infos.get(0).roleKey()).isEqualTo("PROJECT_HIGHEST_DECISION_MAKER");
        assertThat(infos.get(0).infoKey()).isEqualTo("NAME");
        assertThat(infos.get(0).value()).isEqualTo("张三");
    }

    // ---------- Test 4: CRM 商机查询返回空 → 降级返回原 req ----------

    @Test
    @DisplayName("CRM 商机查询返回空列表时降级返回原 req")
    void testSyncFromCrm_chanceQueryReturnsEmpty_degradesToOriginalReq() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId(CC_CODE);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        when(crmOpportunityCodeResolver.resolve(CC_CODE)).thenReturn(CC_CODE);
        when(crmChanceService.findByCode(CC_CODE, USERNAME)).thenReturn(null);

        // when
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then
        assertThat(result).isSameAs(userReq);
        verify(crmContactPersonService, never()).pageList(anyLong(), anyString());
    }

    // ---------- Test 5: CRM 对接人查询返回空 → basic 仍来自 CRM，customerInfos 为空列表 ----------

    @Test
    @DisplayName("CRM 对接人查询返回空列表时 basic 来自 CRM，customerInfos 为空列表（部分降级）")
    void testSyncFromCrm_contactsQueryReturnsEmpty_basicFromCrm_customerInfosEmpty() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId(CC_CODE);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        when(crmOpportunityCodeResolver.resolve(CC_CODE)).thenReturn(CC_CODE);
        when(crmChanceService.findByCode(CC_CODE, USERNAME)).thenReturn(buildCrmChance());
        when(crmContactPersonService.pageList(CHANCE_ID, USERNAME)).thenReturn(Collections.emptyList());

        // when
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then
        assertThat(result).isNotSameAs(userReq);
        // basic 仍来自 CRM
        EvaluationBasicDTO basic = result.evaluationBasic();
        assertThat(basic).isNotNull();
        assertThat(basic.riskAssessment()).isEqualTo("风险较高");
        // customerInfos 为空列表
        assertThat(result.evaluationCustomerInfos()).isEmpty();
        // 保留用户建议
        assertThat(result.bidRecommendation()).isEqualTo(BidRecommendation.RECOMMEND);
    }

    // ---------- Test 6: crmOpportunityId 为纯数字 → 调用 CrmOpportunityCodeResolver 解析 ----------

    @Test
    @DisplayName("tender.crmOpportunityId 为纯数字时调用 CrmOpportunityCodeResolver 解析为 CC code")
    void testSyncFromCrm_numericCrmOpportunityId_callsResolver() {
        // given
        String numericId = "20942";
        Tender tender = new Tender();
        tender.setCrmOpportunityId(numericId);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        when(crmOpportunityCodeResolver.resolve(numericId)).thenReturn(CC_CODE);
        when(crmChanceService.findByCode(CC_CODE, USERNAME)).thenReturn(buildCrmChance());
        when(crmContactPersonService.pageList(CHANCE_ID, USERNAME)).thenReturn(buildContactPersons());

        // when
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then
        verify(crmOpportunityCodeResolver).resolve(numericId);
        verify(crmChanceService).findByCode(CC_CODE, USERNAME);
        // 结果 basic 来自 CRM
        assertThat(result.evaluationBasic().riskAssessment()).isEqualTo("风险较高");
    }

    // ---------- Test 7: CRM 调用抛 RuntimeException → 降级不抛异常（事务安全保证） ----------
    // CO-526 事务安全：syncFromCrm 在 submit 主事务内执行，CRM 异常必须被 catch 降级，
    // 不能让异常逃逸 submit 边界导致事务被标记 rollback-only。

    @Test
    @DisplayName("CRM 商机查询抛 RuntimeException 时降级返回原 req，不抛异常（保证 submit 事务不回滚）")
    void testSyncFromCrm_chanceQueryThrowsRuntimeException_degradesWithoutThrowing() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId(CC_CODE);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        when(crmOpportunityCodeResolver.resolve(CC_CODE)).thenReturn(CC_CODE);
        when(crmChanceService.findByCode(CC_CODE, USERNAME))
                .thenThrow(new RuntimeException("CRM HTTP timeout"));

        // when — 不抛异常 = 事务边界安全
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then — 降级返回原 req
        assertThat(result).isSameAs(userReq);
        verify(crmContactPersonService, never()).pageList(anyLong(), anyString());
    }

    @Test
    @DisplayName("CRM 对接人查询抛 RuntimeException 时 basic 仍来自 CRM，customerInfos 为空（保证 submit 事务不回滚）")
    void testSyncFromCrm_contactsQueryThrowsRuntimeException_degradesWithoutThrowing() {
        // given
        Tender tender = new Tender();
        tender.setCrmOpportunityId(CC_CODE);
        TenderEvaluationSubmitRequest userReq = buildUserReq();

        when(crmOpportunityCodeResolver.resolve(CC_CODE)).thenReturn(CC_CODE);
        when(crmChanceService.findByCode(CC_CODE, USERNAME)).thenReturn(buildCrmChance());
        when(crmContactPersonService.pageList(CHANCE_ID, USERNAME))
                .thenThrow(new RuntimeException("CRM HTTP timeout"));

        // when — 不抛异常 = 事务边界安全
        TenderEvaluationSubmitRequest result = syncService.syncFromCrm(tender, userReq, USERNAME);

        // then — 部分降级：basic 来自 CRM，customerInfos 为空
        assertThat(result).isNotSameAs(userReq);
        assertThat(result.evaluationBasic().riskAssessment()).isEqualTo("风险较高");
        assertThat(result.evaluationCustomerInfos()).isEmpty();
    }

    // ---------- helpers ----------

    private TenderEvaluationSubmitRequest buildUserReq() {
        EvaluationBasicDTO userBasic = new EvaluationBasicDTO(
                3, new BigDecimal("100"), "用户填的不利项", "用户填的风险",
                "否", "否", "用户填的备注", "用户填的GAP", new BigDecimal("500"),
                null);
        EvaluationCustomerInfoDTO userInfo = new EvaluationCustomerInfoDTO(
                "PROJECT_HIGHEST_DECISION_MAKER", "NAME", "用户填的名字", "TEXT");
        return new TenderEvaluationSubmitRequest(
                BidRecommendation.RECOMMEND, userBasic, List.of(userInfo),
                new EvaluationRecommendationDTO(true, "建议投标"));
    }

    private CustomerChanceVO buildCrmChance() {
        return new CustomerChanceVO(
                CHANCE_ID,                          // 1 id
                CC_CODE,                            // 2 code
                "测试商机",                          // 3 name
                null, null, null, null,             // 4-7 groupName, groupId, tenderSubject, tenderSubjectId
                null, null, null, null,              // 8-11 projectLeaderName, projectLeaderNo, secondDeptLeader*
                null, null, null, null, null,        // 12-16 projectStatus, projectStatusText, cooperationStatus, winningVendor, bidFailureReason
                null, null, null,                   // 17-19 missReason, feedBack, projectRisk
                null,                               // 20 projectRiskText
                "2026-06-10",                       // 21 evaluationTime
                5L,                                 // 22 planSupplierCount
                new BigDecimal("150.5"),            // 23 ecommerceMroAmount
                new BigDecimal("800"),              // 24 customerRevenue
                "技术参数不匹配",                     // 25 bidDocumentDisadvantage
                "风险较高",                           // 26 riskPrediction
                true,                               // 27 backupPlan
                null,                               // 28 backupPlanText
                "了解流程",                           // 29 managerUnderstandProcess
                "存在GAP",                           // 30 projectGap
                null,                               // 31 gapFile
                "重要备注",                           // 32 remark
                null, null, null, null,              // 33-36 createBy/Name, updateBy/Name
                null, null, null,                    // 37-39 createAt, updateAt, activeRecord
                null, null, null, null               // 40-43 activeRecordTime, activeRecordCreateBy, transferVisible, bidRemainTime
        );
    }

    private List<ContactPersonInfoVO> buildContactPersons() {
        return List.of(new ContactPersonInfoVO(
                1L, "张三", "13800138000", "zhangsan@example.com",
                "1", "西域小李", true, "1", "支持", "依据A",
                null, true, true, false, true, false, "80%",
                null, null, null, null, null, null));
    }
}
