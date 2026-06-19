package com.xiyu.bid.integration.external;

import com.xiyu.bid.crm.application.CrmProjectLeaderService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrmTenderLinkService} 单元测试。
 * <p>覆盖 CO-252 测试要点：
 * <ol>
 *   <li>传入 crmId → 状态自动变为 EVALUATED</li>
 *   <li>项目负责人自动分配（按工号匹配本地用户）</li>
 *   <li>商机自动关联</li>
 *   <li>不传 crmId 时行为不变</li>
 *   <li>CRM 接口异常时降级（保持 PENDING_ASSIGNMENT）</li>
 *   <li>未找到负责人时仍关联商机并设为 EVALUATED</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmTenderLinkServiceTest {

    @Mock private CrmProjectLeaderService crmProjectLeaderService;
    @Mock private UserRepository userRepository;

    private CrmTenderLinkService service;

    @BeforeEach
    void setUp() {
        service = new CrmTenderLinkService(crmProjectLeaderService, userRepository);
    }

    private Tender newTender() {
        Tender t = new Tender();
        t.setStatus(Tender.Status.PENDING_ASSIGNMENT);
        return t;
    }

    // ===== 测试要点 1+2+3：传入 crmId → EVALUATED + 负责人分配 + 商机关联 =====

    @Test
    void linkIfPresent_withCrmId_assignsLeaderAndSetsEvaluated() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC001");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC001")).thenReturn(leader);

        User user = new User();
        user.setId(50L);
        user.setFullName("张三");
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));

        service.linkIfPresent(tender, "CC001");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getProjectManagerId()).isEqualTo(50L);
        assertThat(tender.getProjectManagerName()).isEqualTo("张三");
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC001");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机A");
    }

    // ===== 测试要点 4：不传 crmId 时行为不变 =====

    @Test
    void linkIfPresent_nullCrmId_noOp() {
        Tender tender = newTender();

        service.linkIfPresent(tender, null);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any());
        verify(userRepository, never()).findByEmployeeNumber(any());
    }

    @Test
    void linkIfPresent_blankCrmId_noOp() {
        Tender tender = newTender();

        service.linkIfPresent(tender, "   ");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any());
    }

    // ===== 降级场景 1：未找到负责人 → 仍关联商机并设为 EVALUATED =====

    @Test
    void linkIfPresent_noLeader_linksOpportunityAndSetsEvaluated() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC002")).thenReturn(null);

        service.linkIfPresent(tender, "CC002");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC002");
        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getProjectManagerName()).isNull();
    }

    // ===== 降级场景 2：CRM 接口异常 → 保持 PENDING_ASSIGNMENT =====

    @Test
    void linkIfPresent_crmServiceThrows_keepsPendingAssignment() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC003"))
                .thenThrow(new RuntimeException("CRM 服务不可用"));

        service.linkIfPresent(tender, "CC003");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    // ===== 降级场景 3：工号未匹配本地用户 → 用姓名兜底 =====

    @Test
    void linkIfPresent_employeeNoNotMatched_fallsBackToNameOnly() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "李四", "EMP999", "商机B", "CC004");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC004")).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP999")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, "CC004");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getProjectManagerId()).isNull();
        assertThat(tender.getProjectManagerName()).isEqualTo("李四");
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC004");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机B");
    }

    // ===== 降级场景 4：负责人无工号 → 直接用姓名 =====

    @Test
    void linkIfPresent_leaderWithoutEmployeeNo_usesNameDirectly() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "王五", null, "商机C", "CC005");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC005")).thenReturn(leader);

        service.linkIfPresent(tender, "CC005");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getProjectManagerName()).isEqualTo("王五");
        assertThat(tender.getProjectManagerId()).isNull();
        verify(userRepository, never()).findByEmployeeNumber(any());
    }

    // ===== CO-275：linkByChanceIdIfPresent 兜底反查 =====

    @Test
    void linkByChanceIdIfPresent_crmSourceWithNumericSourceId_looksUpByChanceId() {
        Tender tender = newTender();
        // detail 接口反查到商机编号 CC20260619283
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC20260619283");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(243L)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "243");

        assertThat(linked).isTrue();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机A");
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(243L);
    }

    @Test
    void linkByChanceIdIfPresent_nonCrmSource_returnsFalseNoLookup() {
        Tender tender = newTender();

        boolean linked = service.linkByChanceIdIfPresent(tender, "EXTERNAL", "243");

        assertThat(linked).isFalse();
        assertThat(tender.getCrmOpportunityId()).isNull();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any());
    }

    @Test
    void linkByChanceIdIfPresent_nonNumericSourceId_returnsFalseNoLookup() {
        Tender tender = newTender();

        // sourceId 不是数字（如第三方平台的字母数字 id），不是商机主键，跳过
        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "ABC-243");

        assertThat(linked).isFalse();
        assertThat(tender.getCrmOpportunityId()).isNull();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any());
    }

    @Test
    void linkByChanceIdIfPresent_detailReturnsNull_returnsFalseKeepsPending() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(999L)).thenReturn(null);

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "999");

        assertThat(linked).isFalse();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    @Test
    void linkByChanceIdIfPresent_detailThrows_returnsFalseKeepsPending() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(243L))
                .thenThrow(new RuntimeException("CRM 服务不可用"));

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "243");

        assertThat(linked).isFalse();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    // ===== CO-277：applyCrmLinkAndAssignment 识别纯数字 id 并按 id 反查 code =====

    @Test
    void linkIfPresent_numericId_looksUpByChanceIdAndStoresCode() {
        // CRM 推送 crmOpportunityId=20916（商机主键 id），应按 id 反查拿 code 存入
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "cye测试3", "CC20260619285");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, "20916");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        // 关键断言：存的是反查到的 code（CC... 格式），不是原始 id（20916）
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619285");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("cye测试3");
        // 应按 id 查，不应按 code 查
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(20916L);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any());
    }

    @Test
    void linkIfPresent_numericId_detailReturnsNull_doesNotStoreId() {
        // id 格式反查失败时，不能把 id 存入 crm_opportunity_id（会让兜底跳过 + 回传 code 错误）
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L)).thenReturn(null);

        service.linkIfPresent(tender, "20916");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        // 关键断言：id 格式反查失败时保持 null，不存 20916
        assertThat(tender.getCrmOpportunityId()).isNull();
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(20916L);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any());
    }

    @Test
    void linkIfPresent_codeFormat_stillUsesChanceCodeLookup() {
        // code 格式（CC...）保持原逻辑，走 findProjectLeaderByChanceCode
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC20260619285");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC20260619285")).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, "CC20260619285");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619285");
        verify(crmProjectLeaderService).findProjectLeaderByChanceCode("CC20260619285");
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any());
    }
}
