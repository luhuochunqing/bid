package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CrmCustomerLeaderQueryService;
import com.xiyu.bid.crm.application.CustomerLeaderResult;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.crm.domain.AssignmentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TenderAutoAssignmentServiceTest {

    @Mock
    private CrmProjectMappingRepository mappingRepository;

    @Mock
    private CrmCustomerLeaderQueryService customerLeaderQueryService;

    private TenderAutoAssignmentService autoAssignmentService;
    private Tender tender;

    @BeforeEach
    void setUp() {
        autoAssignmentService = new TenderAutoAssignmentService(mappingRepository, customerLeaderQueryService);
        tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .purchaserName("上海西域采购中心")
                .budget(new BigDecimal("150.00"))
                .publishDate(LocalDate.now())
                .deadline(LocalDateTime.now().plusDays(20))
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();
    }

    @Test
    @DisplayName("根据业主单位匹配成功 - 返回负责人信息")
    void tryAutoAssign_MatchFound_ShouldReturnManagerInfo() {
        CrmProjectMapping mapping = CrmProjectMapping.builder()
                .id(1L)
                .purchaserName("上海西域采购中心")
                .crmProjectId("CRM-001")
                .projectManagerId("PM-001")
                .projectManagerName("张三")
                .departmentId("DEPT-001")
                .departmentName("销售部")
                .build();

        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.of(mapping));

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.crmProjectId()).isEqualTo("CRM-001");
        assertThat(result.projectManagerId()).isEqualTo("PM-001");
        assertThat(result.projectManagerName()).isEqualTo("张三");
        assertThat(result.departmentId()).isEqualTo("DEPT-001");
        assertThat(result.departmentName()).isEqualTo("销售部");
        verify(mappingRepository).findByPurchaserName("上海西域采购中心");
    }

    @Test
    @DisplayName("根据业主单位匹配失败 - 返回 noMatch")
    void tryAutoAssign_NoMatch_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.crmProjectId()).isNull();
        assertThat(result.projectManagerId()).isNull();
        assertThat(result.projectManagerName()).isNull();
    }

    @Test
    @DisplayName("purchaserName 为空 - 返回 noMatch")
    void tryAutoAssign_BlankPurchaserName_ShouldReturnNoMatch() {
        tender.setPurchaserName(null);

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("tender 为空 - 返回 noMatch")
    void tryAutoAssign_NullTender_ShouldReturnNoMatch() {
        AssignmentResult result = autoAssignmentService.tryAutoAssign(null);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("purchaserName 前后有空格 - 自动 trim 后匹配")
    void tryAutoAssign_TrimmedPurchaserName_ShouldMatch() {
        tender.setPurchaserName("  上海西域采购中心  ");
        CrmProjectMapping mapping = CrmProjectMapping.builder()
                .purchaserName("上海西域采购中心")
                .projectManagerName("李四")
                .build();

        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.of(mapping));

        AssignmentResult result = autoAssignmentService.tryAutoAssign(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerName()).isEqualTo("李四");
    }

    // ── CO-302: CRM 自动分配测试 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("autoAssignIfPossible_本地映射匹配成功_不调用CRM")
    void autoAssignIfPossible_LocalMatch_ShouldNotCallCrm() {
        CrmProjectMapping mapping = CrmProjectMapping.builder()
                .purchaserName("上海西域采购中心")
                .projectManagerName("王五")
                .projectManagerId("10086")
                .build();

        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.of(mapping));

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerName()).isEqualTo("王五");
        assertThat(result.projectManagerId()).isEqualTo("10086");
        verify(customerLeaderQueryService, never()).findLeaderByGroupName(anyString());
    }

    @Test
    @DisplayName("autoAssignIfPossible_本地匹配失败_CRM匹配成功")
    void autoAssignIfPossible_LocalNoMatch_CrmMatch_ShouldReturnCrmResult() {
        // 本地映射表无匹配
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());

        // CRM 返回负责人信息
        CustomerLeaderResult crmLeader =
                new CustomerLeaderResult("上海西域采购中心", "张三", "10001");
        when(customerLeaderQueryService.findLeaderByGroupName("上海西域采购中心"))
                .thenReturn(crmLeader);

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerName()).isEqualTo("张三");
        assertThat(result.projectManagerId()).isEqualTo("10001");
        verify(mappingRepository).findByPurchaserName("上海西域采购中心");
        verify(customerLeaderQueryService).findLeaderByGroupName("上海西域采购中心");
    }

    @Test
    @DisplayName("autoAssignIfPossible_本地和CRM都匹配失败_返回noMatch")
    void autoAssignIfPossible_BothNoMatch_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(customerLeaderQueryService.findLeaderByGroupName("上海西域采购中心"))
                .thenReturn(null);

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("autoAssignIfPossible_CRM调用异常_降级为noMatch")
    void autoAssignIfPossible_CrmException_ShouldReturnNoMatch() {
        when(mappingRepository.findByPurchaserName("上海西域采购中心"))
                .thenReturn(Optional.empty());
        when(customerLeaderQueryService.findLeaderByGroupName("上海西域采购中心"))
                .thenThrow(new RuntimeException("CRM connection timeout"));

        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(tender);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("tryAutoAssignFromCrm_purchaserName为空_返回noMatch")
    void tryAutoAssignFromCrm_BlankPurchaserName_ShouldReturnNoMatch() {
        tender.setPurchaserName(null);

        AssignmentResult result = autoAssignmentService.tryAutoAssignFromCrm(tender);

        assertThat(result.isMatched()).isFalse();
        verify(customerLeaderQueryService, never()).findLeaderByGroupName(anyString());
    }

    @Test
    @DisplayName("tryAutoAssignFromCrm_CRM返回负责人信息_匹配成功")
    void tryAutoAssignFromCrm_CrmLeaderFound_ShouldReturnMatched() {
        CustomerLeaderResult crmLeader =
                new CustomerLeaderResult("上海西域采购中心", "李四", "10002");
        when(customerLeaderQueryService.findLeaderByGroupName("上海西域采购中心"))
                .thenReturn(crmLeader);

        AssignmentResult result = autoAssignmentService.tryAutoAssignFromCrm(tender);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.projectManagerName()).isEqualTo("李四");
        assertThat(result.projectManagerId()).isEqualTo("10002");
    }

    @Test
    @DisplayName("tryAutoAssignFromCrm_CRM返回null_返回noMatch")
    void tryAutoAssignFromCrm_CrmReturnsNull_ShouldReturnNoMatch() {
        when(customerLeaderQueryService.findLeaderByGroupName("上海西域采购中心"))
                .thenReturn(null);

        AssignmentResult result = autoAssignmentService.tryAutoAssignFromCrm(tender);

        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("autoAssignIfPossible_tender 为空返回 noMatch")
    void autoAssignIfPossible_NullTender_ShouldReturnNoMatch() {
        AssignmentResult result = autoAssignmentService.autoAssignIfPossible(null);

        assertThat(result.isMatched()).isFalse();
    }
}
