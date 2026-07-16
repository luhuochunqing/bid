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

        service.linkIfPresent(tender, null, "CC001", null);

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

        service.linkIfPresent(tender, "20916", "CC001", null);

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

        service.linkIfPresent(tender, null, null, null);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        assertThat(tender.getCrmOpportunityId()).isNull();
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any(), any());
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkIfPresent_bothBlank_noOp() {
        Tender tender = newTender();

        service.linkIfPresent(tender, "  ", "  ", null);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceCode(any(), any());
    }

    // ===== 降级：未找到负责人 → 仍关联商机并设为 EVALUATED =====

    @Test
    void linkIfPresent_codeOnly_noLeader_storesCodeAndSetsEvaluated() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC002", null)).thenReturn(null);

        service.linkIfPresent(tender, null, "CC002", null);

        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC002");
        assertThat(tender.getProjectManagerId()).isNull();
    }

    @Test
    void linkIfPresent_crmIdOnly_noLeader_storesNothingButSetsEvaluated() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L, null)).thenReturn(null);

        service.linkIfPresent(tender, "20916", null, null);

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

        service.linkIfPresent(tender, null, "CC003", null);

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

        service.linkIfPresent(tender, "20916", "CC004", null);

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

        service.linkIfPresent(tender, null, "CC005", null);

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

        service.linkIfPresent(tender, null, "CC006", null);

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

        service.linkIfPresent(tender, "not-a-number", "CC007", null);

        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
        verify(crmProjectLeaderService).findProjectLeaderByChanceCode("CC007", null);
    }

    // ===== linkByChanceIdIfPresent 兜底反查（不变） =====

    @Test
    void linkByChanceIdIfPresent_crmSourceWithNumericSourceId_looksUpByBidId() {
        // spec 037: sourceId 是 bidId（标讯 ID），改用 findProjectLeaderByBidId 走 page-list
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC20260619283");
        when(crmProjectLeaderService.findProjectLeaderByBidId(243L, null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "243", null);

        assertThat(linked).isTrue();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC20260619283");
        verify(crmProjectLeaderService).findProjectLeaderByBidId(243L, null);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkByChanceIdIfPresent_nonCrmSource_returnsFalse() {
        Tender tender = newTender();

        boolean linked = service.linkByChanceIdIfPresent(tender, "EXTERNAL", "243", null);

        assertThat(linked).isFalse();
        verify(crmProjectLeaderService, never()).findProjectLeaderByBidId(any(), any());
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkByChanceIdIfPresent_nonNumericSourceId_returnsFalse() {
        Tender tender = newTender();

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "ABC-243", null);

        assertThat(linked).isFalse();
        verify(crmProjectLeaderService, never()).findProjectLeaderByBidId(any(), any());
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkByChanceIdIfPresent_detailReturnsNull_returnsFalse() {
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByBidId(999L, null)).thenReturn(null);

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "999", null);

        assertThat(linked).isFalse();
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.PENDING_ASSIGNMENT);
    }

    // ===== spec 037: sourceId 是 bidId（标讯 ID），不是 chanceId（商机主键）=====
    // 生产 bug：tender 56 的 external_id=CRM:7，7 是 CRM 标讯 ID（bidId），
    // 旧代码把 7 当 chanceId 调 detail 接口 → 查不到商机（实际商机 id=6, bidId=7）
    // 修复：改用 page-list 按 bidId 反查（findProjectLeaderByBidId）

    @Test
    void linkByBidIdIfPresent_shouldResolveByBidIdNotChanceId() {
        // Given: sourceId="7" 是 bidId（标讯 ID），mock findProjectLeaderByBidId 返回 leader
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "王旭州", "04503", "中国旅游集团 2026年-2029年电子超市（内地）集中采购", "CC2026071568");
        when(crmProjectLeaderService.findProjectLeaderByBidId(7L, "04503")).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("04503")).thenReturn(Optional.empty());

        // When: 调 linkByBidIdIfPresent（新方法名，语义清晰）
        boolean linked = service.linkByBidIdIfPresent(tender, "CRM", "7", "04503");

        // Then: 关联成功，crmOpportunityId 是 CC 编号
        assertThat(linked).isTrue();
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC2026071568");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("中国旅游集团 2026年-2029年电子超市（内地）集中采购");
        assertThat(tender.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        // Verify: 从未调 findProjectLeaderByChanceId（证明不再用 detail 接口）
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
    }

    @Test
    void linkByChanceIdIfPresent_legacyMethodName_alsoUsesBidIdLookup() {
        // 兼容性验证：旧方法名 linkByChanceIdIfPresent 也改用 findProjectLeaderByBidId
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "王旭州", "04503", "商机A", "CC2026071568");
        when(crmProjectLeaderService.findProjectLeaderByBidId(7L, null)).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("04503")).thenReturn(Optional.empty());

        boolean linked = service.linkByChanceIdIfPresent(tender, "CRM", "7", null);

        assertThat(linked).isTrue();
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC2026071568");
        // 验证走 findProjectLeaderByBidId（page-list），而非 findProjectLeaderByChanceId（detail）
        verify(crmProjectLeaderService).findProjectLeaderByBidId(7L, null);
        verify(crmProjectLeaderService, never()).findProjectLeaderByChanceId(any(), any());
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

        service.linkIfPresent(tender, "16", null, null);

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

        service.linkIfPresent(tender, null, "21364", null);

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

        service.linkIfPresent(tender, null, "21364", null);

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

        service.linkIfPresent(tender, null, "21364", null);

        assertThat(tender.getCrmOpportunityId()).isNull();
    }

    // ===== 根因行为测试：API Key 路径下传 username，反查能拿到 CRM token =====
    // 生产 bug：tender id=34, crmOpportunityId=34（纯数字），username=null →
    //          CrmAuthService.getValidTokenForUser(null) 抛异常 → 反查失败 → 商机未关联
    // 修复：linkIfPresent 新增 username 参数，透传到 findProjectLeaderByChanceId

    @Test
    void linkIfPresent_numericCode_withUsername_passesUsernameToChanceIdLookup() {
        // 根因行为：API Key 认证路径下，调用方传入 username（通过 userId 反查得到），
        // findProjectLeaderByChanceId 应收到非 null 的 username，使 CrmAuthService 能拿到 CRM token
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "0712创建标讯", "CC2026071244");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(34L, "admin")).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, null, "34", "admin");

        // 验证 username 被透传到底层反查
        verify(crmProjectLeaderService).findProjectLeaderByChanceId(34L, "admin");
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC2026071244");
        assertThat(tender.getCrmOpportunityName()).isEqualTo("0712创建标讯");
    }

    @Test
    void linkIfPresent_crmIdOnly_withUsername_passesUsernameToChanceIdLookup() {
        // 根因行为：crmId 路径同样需要透传 username
        Tender tender = newTender();
        CrmProjectLeaderService.ProjectLeaderResult leader =
                new CrmProjectLeaderService.ProjectLeaderResult(
                        "张三", "EMP001", "商机A", "CC001");
        when(crmProjectLeaderService.findProjectLeaderByChanceId(20916L, "admin")).thenReturn(leader);
        when(userRepository.findByEmployeeNumber("EMP001")).thenReturn(Optional.empty());

        service.linkIfPresent(tender, "20916", null, "admin");

        verify(crmProjectLeaderService).findProjectLeaderByChanceId(20916L, "admin");
    }

    @Test
    void linkIfPresent_codeOnly_withUsername_passesUsernameToChanceCodeLookup() {
        // 根因行为：CC 格式 code 路径也透传 username
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceCode("CC001", "admin")).thenReturn(null);

        service.linkIfPresent(tender, null, "CC001", "admin");

        verify(crmProjectLeaderService).findProjectLeaderByChanceCode("CC001", "admin");
        assertThat(tender.getCrmOpportunityId()).isEqualTo("CC001");
    }

    @Test
    void linkIfPresent_usernameNull_fallsBackToNullUsername() {
        // 兼容性：userId=null（无创建者）时降级为 null，保持旧行为
        Tender tender = newTender();
        when(crmProjectLeaderService.findProjectLeaderByChanceId(34L, null)).thenReturn(null);

        service.linkIfPresent(tender, null, "34", null);

        verify(crmProjectLeaderService).findProjectLeaderByChanceId(34L, null);
    }
}
