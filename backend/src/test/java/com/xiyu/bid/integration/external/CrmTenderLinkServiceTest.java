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
 * <p>crmId（数字主键）和 crmOpportunityCode（CC... 格式）是独立字段：
 * <ul>
 *   <li>code 非空 → 直接存入 tender.crm_opportunity_id，不依赖 CRM API</li>
 *   <li>crmId 非空 → 用于 findProjectLeaderByChanceId 查项目负责人</li>
 *   <li>code 非空且 crmId 未命中 → 用 code 走 findProjectLeaderByChanceCode</li>
 * </ul>
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

    // ===== code 直接存入 =====

    @Test
    void linkIfPresent_codeProvided_storesDirectlyBeforeApiCall() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC001", null)).thenReturn(null);

        service.linkIfPresent(tender, null, "CC001");

        // code 直接存入，即使 API 没找到负责人
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC001");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    void linkIfPresent_bothProvided_storesCodeAndUsesCrmIdForLookup() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC001");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L, null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, "20916", "CC001");

        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC001");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("商机A");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        // 应优先用 crmId 查
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(20916L, null);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any(), any());
    }

    // ===== 无参数 → no-op =====

    @Test
    void linkIfPresent_bothNull_noOp() {
        Tender tender = newTender();

        service.linkIfPresent(tender, null, null);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any(), any());
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkIfPresent_bothBlank_noOp() {
        Tender tender = newTender();

        service.linkIfPresent(tender, "  ", "  ");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any(), any());
    }

    // ===== 降级：未找到负责人 → 仍关联商机并设为 EVALUATED =====

    @Test
    void linkIfPresent_codeOnly_noLeader_storesCodeAndSetsEvaluated() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC002", null)).thenReturn(null);

        service.linkIfPresent(tender, null, "CC002");

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC002");
        assertThat(tender.getProjectManagerId()).isNull();
    }

    @Test
    void linkIfPresent_crmIdOnly_noLeader_storesNothingButSetsEvaluated() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L, null)).thenReturn(null);

        service.linkIfPresent(tender, "20916", null);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        // 无 code 时不存入
        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    // ===== 降级：CRM 接口异常 → code 已存入 =====

    @Test
    void linkIfPresent_crmApiThrows_codeStillStored() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC003", null))
                .thenThrow(new RuntimeException("CRM 服务不可用"));

        service.linkIfPresent(tender, null, "CC003");

        // code 在 API 调用前就已存入
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC003");
    }

    // ===== 负责人分配 =====

    @Test
    void linkIfPresent_leaderFound_assignsManager() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC004");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L, null)).thenReturn(leader);
        User user = new User();
        user.setId(50L);
        user.setFullName("张三");
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.of(user));

        service.linkIfPresent(tender, "20916", "CC004");

        assertThat(tender.getProjectManagerId()).isEqualTo(50L);
        assertThat(tender.getProjectManagerName()).isEqualTo("张三");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    void linkIfPresent_leaderFound_employeeNoNotMatched_fallsBackToName() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "李四", "EMP999", "商机B", "CC005");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC005", null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP999")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, null, "CC005");

        assertThat(tender.getProjectManagerName()).isEqualTo("李四");
        assertThat(tender.getProjectManagerId()).isNull();
    }

    @Test
    void linkIfPresent_leaderWithoutEmployeeNo_usesNameDirectly() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "王五", null, "商机C", "CC006");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC006", null)).thenReturn(leader);

        service.linkIfPresent(tender, null, "CC006");

        assertThat(tender.getProjectManagerName()).isEqualTo("王五");
        verify(userRepository, never()).findByEmployeeNumber(any());
    }

    // ===== crmId 非数字 → 跳过 chanceId 查询，用 code 查 =====

    @Test
    void linkIfPresent_nonNumericCrmId_fallsBackToCodeLookup() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC007");
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC007", null)).thenReturn(leader);

        service.linkIfPresent(tender, "not-a-number", "CC007");

        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
        verify(crmProjectLeaderService).findProjectLeaderByChanceCode("CC007", null);
    }

    // ===== linkByChanceIdIfPresent 兜底反查（不变） =====

    @Test
    void linkByChanceIdIfPresent_crmSourceWithNumericSourceId_looksUpByChanceId() {
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC20260619283");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(243L, null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "243");

        assertThat(linked).isTrue();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
    }

    @Test
    void linkByChanceIdIfPresent_nonCrmSource_returnsFalse() {
        Tender tender = newTender();

        boolean linked = service.linkByChanceIdIfPresent(tender, "EXTERNAL", "243");

        assertThat(linked).isFalse();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkByChanceIdIfPresent_nonNumericSourceId_returnsFalse() {
        Tender tender = newTender();

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "ABC-243");

        assertThat(linked).isFalse();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkByChanceIdIfPresent_detailReturnsNull_returnsFalse() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(999L, null)).thenReturn(null);

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "999");

        assertThat(linked).isFalse();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    // ===== 防"半关联"：leader.code 为空时不应存入 name（生产 bug 修复回归） =====

    @Test
    void linkIfPresent_crmIdOnly_leaderCodeNull_doesNotSetHalfLinkState() {
        Tender tender = newTender();
        // CRM 推送只传 crmId=16，CRM detail 返回 leader 但 code=null（商机未返回 code）
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "0710商机10", null);
        when(crmProjectLeaderService.findProjectLeaderByChanceId(16L, null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, "16", null);

        // 防半关联：code=null 时 id 和 name 都不应被设置
        assertThat(tender.getCrmOpportunityId()).isNull();
        assertThat(tender.getCrmOpportunityName()).isNull();
        // 但状态和负责人分配照常（不影响业务）
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getProjectManagerName()).isEqualTo("张三");
    }

    // ===== CO-277 回归：CRM 推送 crmOpportunityId 字段传纯数字主键 id =====

    @Test
    void linkIfPresent_numericCode_resolvesViaChanceIdLookup() {
        // PR !2011 回归根因：CRM 推送 crmOpportunityId=21364（纯数字主键 id），
        // 旧代码直接存入 crm_opportunity_id 列，与"关联标讯"按钮设置的 CC 格式编号不一致，
        // 导致去重校验失效（tender 1646 案例）
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "0711关联商机测试", "CC20260711739");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(21364L, null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, null, "21364");

        // 纯数字 id 不直接存入，反查后存入 CC 格式编号
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260711739");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("0711关联商机测试");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(21364L, null);
        // 不应该用纯数字去调 findProjectLeaderByChanceCode
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any(), any());
    }

    @Test
    void linkIfPresent_numericCode_noLeaderFound_doesNotStoreNumericId() {
        // CRM 反查失败时，纯数字 id 不应存入 crm_opportunity_id 列
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(21364L, null)).thenReturn(null);

        service.linkIfPresent(tender, null, "21364");

        // 纯数字 id 不存入，避免与 CC 格式编号不一致导致去重校验失效
        assertThat(tender.getCrmOpportunityId()).isNull();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    void linkIfPresent_numericCode_crmApiThrows_doesNotStoreNumericId() {
        // CRM API 异常时，纯数字 id 仍不应存入
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(21364L, null))
                .thenThrow(new RuntimeException("CRM 服务不可用"));

        service.linkIfPresent(tender, null, "21364");

        assertThat(tender.getCrmOpportunityId()).isNull();
    }
}
