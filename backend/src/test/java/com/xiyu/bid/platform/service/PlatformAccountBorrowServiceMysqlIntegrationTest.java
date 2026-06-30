package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.entity.PlatformAccount.PlatformType;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.support.AbstractMysqlIntegrationTest;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlatformAccountBorrowService 真实 MySQL 集成测试。
 *
 * 覆盖 Mock 单元测试无法验证的场景：
 * - JPA entity → MySQL round-trip（collation、约束、enum 持久化）
 * - 跨表事务一致性（platform_accounts + account_borrow_applications）
 * - @Transactional 回滚验证
 * - syncReturnedApplication（CO-403 关键修复，完全未测）
 *
 * 不使用 disabledWithoutDocker=true，Docker 不可用时 fail-fast。
 *
 * ddl-auto: 本测试覆盖为 none（profile 默认是 validate）。
 * 原因：本测试聚焦 PlatformAccountBorrowService 的业务逻辑 + JPA round-trip，
 * 不负责全库 schema 漂移审计。profile 级 validate 由 FlywayMysqlContainerTest 承担。
 * 本 PR 已修复被测表的 schema 漂移：V1113 (account_borrow_applications.status VARCHAR→ENUM)。
 * 其他无关表漂移（bid_document_review 等）留单独 PR 修复后，可移除此覆盖。
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("flyway-mysql")
@Import(NoOpPasswordEncryptionTestConfig.class)
class PlatformAccountBorrowServiceMysqlIntegrationTest extends AbstractMysqlIntegrationTest {

    @Autowired
    private PlatformAccountBorrowService borrowService;

    @Autowired
    private PlatformAccountRepository accountRepository;

    @Autowired
    private AccountBorrowApplicationRepository applicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final Long CUSTODIAN_ID = 9001L;
    private static final Long APPLICANT_ID = 9002L;
    private static final Long OTHER_USER_ID = 9003L;

    @BeforeEach
    void cleanTestData() {
        // 顺序：先删申请（FK 逻辑依赖），再删账号
        jdbcTemplate.update("DELETE FROM account_borrow_applications WHERE applicant_id IN (?, ?, ?) OR custodian_id IN (?, ?, ?)",
                APPLICANT_ID, OTHER_USER_ID, CUSTODIAN_ID, APPLICANT_ID, OTHER_USER_ID, CUSTODIAN_ID);
        jdbcTemplate.update("DELETE FROM platform_accounts WHERE username LIKE 'test-int-%'");
    }

    // ── 辅助方法 ──

    private PlatformAccount createAvailableAccount(String suffix) {
        return accountRepository.saveAndFlush(PlatformAccount.builder()
                .username("test-int-acct-" + suffix)
                .password("encrypted-pwd")
                .accountName("测试账号-" + suffix)
                .contactPerson(CUSTODIAN_ID)
                .platformType(PlatformType.BIDDING_PLATFORM)
                .status(AccountStatus.AVAILABLE)
                .build());
    }

    private User buildUser(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private BorrowApplicationRequest buildRequest(Long accountId, Long custodianId) {
        return BorrowApplicationRequest.builder()
                .accountId(accountId)
                .custodianId(custodianId)
                .purpose("集成测试用途")
                .projectName("集成测试项目")
                .expectedReturnAt(LocalDateTime.now().plusDays(7).toString())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    //  A. CO-386: custodianId fallback（MySQL round-trip + 事务回滚）
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-386: custodianId fallback")
    class CustodianIdFallback {

        @Test
        @DisplayName("A1: 未传 custodianId 时从 account.contactPerson 自动取值，DB 落库正确")
        void submitWithoutCustodianId_usesAccountContactPerson_persistsCorrectly() {
            PlatformAccount account = createAvailableAccount("a1");
            BorrowApplicationRequest request = BorrowApplicationRequest.builder()
                    .accountId(account.getId())
                    .purpose("CO-386 fallback 测试")
                    .expectedReturnAt(LocalDateTime.now().plusDays(3).toString())
                    .build();

            BorrowApplicationDTO result = borrowService.submitApplication(request, buildUser(APPLICANT_ID));

            flushAndClear();

            // 验证 DB 中申请记录的 custodian_id 正确落库
            AccountBorrowApplication saved = applicationRepository.findById(result.getId()).orElseThrow();
            assertEquals(CUSTODIAN_ID, saved.getCustodianId(),
                    "custodianId 应从 account.contactPerson 自动取值");
            assertEquals(BorrowStatus.PENDING_APPROVAL, saved.getStatus());

            // 验证账号状态已变为 PENDING_APPROVAL
            PlatformAccount updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
            assertEquals(AccountStatus.PENDING_APPROVAL, updatedAccount.getStatus());
        }

        @Test
        @DisplayName("A2: 未传 custodianId 且账号未绑定联系人 → 抛异常且无任何记录写入（事务回滚）")
        void submitWithoutCustodianId_accountWithoutContactPerson_throwsAndRollsBack() {
            PlatformAccount account = accountRepository.saveAndFlush(PlatformAccount.builder()
                    .username("test-int-acct-a2")
                    .password("encrypted-pwd")
                    .accountName("测试账号-a2")
                    .contactPerson(null)  // 未绑定联系人
                    .platformType(PlatformType.GOV_PROCUREMENT)
                    .status(AccountStatus.AVAILABLE)
                    .build());

            BorrowApplicationRequest request = BorrowApplicationRequest.builder()
                    .accountId(account.getId())
                    .purpose("应失败的测试")
                    .build();

            assertThrows(BusinessException.class,
                    () -> borrowService.submitApplication(request, buildUser(APPLICANT_ID)));

            flushAndClear();

            // 验证事务回滚：无申请记录，账号状态不变
            List<AccountBorrowApplication> apps = applicationRepository.findByAccountId(account.getId());
            assertTrue(apps.isEmpty(), "事务回滚后不应有申请记录");

            PlatformAccount unchanged = accountRepository.findById(account.getId()).orElseThrow();
            assertEquals(AccountStatus.AVAILABLE, unchanged.getStatus(),
                    "事务回滚后账号状态应保持 AVAILABLE");
        }

        @Test
        @DisplayName("A3: custodianId 与 account.contactPerson 不匹配 → 抛异常且账号状态不变")
        void submitWithMismatchedCustodianId_throwsAndAccountUnchanged() {
            PlatformAccount account = createAvailableAccount("a3");
            BorrowApplicationRequest request = buildRequest(account.getId(), OTHER_USER_ID);  // 错误的 custodianId

            assertThrows(BusinessException.class,
                    () -> borrowService.submitApplication(request, buildUser(APPLICANT_ID)));

            flushAndClear();

            List<AccountBorrowApplication> apps = applicationRepository.findByAccountId(account.getId());
            assertTrue(apps.isEmpty(), "不应有申请记录");

            PlatformAccount unchanged = accountRepository.findById(account.getId()).orElseThrow();
            assertEquals(AccountStatus.AVAILABLE, unchanged.getStatus(),
                    "账号状态应保持 AVAILABLE");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  B. syncReturnedApplication（CO-403 关键修复，完全未测）
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-403: syncReturnedApplication")
    class SyncReturnedApplication {

        @Test
        @DisplayName("B3: BORROWED 申请存在时 → 状态变 RETURNED，returnedAt 填充")
        void syncReturnedApplication_borrowedExists_marksReturned() {
            PlatformAccount account = createAvailableAccount("b3");
            // 先走正常流程到 BORROWED 状态
            BorrowApplicationDTO submitted = borrowService.submitApplication(
                    buildRequest(account.getId(), CUSTODIAN_ID), buildUser(APPLICANT_ID));
            borrowService.approveApplication(submitted.getId(), "approved", buildUser(CUSTODIAN_ID), false);

            flushAndClear();

            // 执行 syncReturnedApplication
            borrowService.syncReturnedApplication(account.getId());

            flushAndClear();

            AccountBorrowApplication synced = applicationRepository.findById(submitted.getId()).orElseThrow();
            assertEquals(BorrowStatus.RETURNED, synced.getStatus(),
                    "申请状态应变为 RETURNED");
            assertNotNull(synced.getReturnedAt(), "returnedAt 应被填充");
        }

        @Test
        @DisplayName("B4: 无 BORROWED 申请时 → no-op，不抛异常")
        void syncReturnedApplication_noBorrowedApplication_noOp() {
            PlatformAccount account = createAvailableAccount("b4");

            // 没有 BORROWED 申请，应正常返回
            borrowService.syncReturnedApplication(account.getId());

            // 验证无副作用
            List<AccountBorrowApplication> apps = applicationRepository.findByAccountId(account.getId());
            assertTrue(apps.isEmpty());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  C+F. 完整生命周期 + 跨表一致性
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("C5+F1+F2+F4: 完整生命周期 submit→approve→return，验证跨表一致性")
    void fullLifecycle_submitApproveReturn_crossTableConsistency() {
        PlatformAccount account = createAvailableAccount("lifecycle");
        User applicant = buildUser(APPLICANT_ID);
        User custodian = buildUser(CUSTODIAN_ID);

        // ── F1: submit 后 account=PENDING_APPROVAL + application=PENDING_APPROVAL ──
        BorrowApplicationDTO submitted = borrowService.submitApplication(
                buildRequest(account.getId(), CUSTODIAN_ID), applicant);

        flushAndClear();

        PlatformAccount afterSubmit = accountRepository.findById(account.getId()).orElseThrow();
        AccountBorrowApplication appAfterSubmit = applicationRepository.findById(submitted.getId()).orElseThrow();
        assertEquals(AccountStatus.PENDING_APPROVAL, afterSubmit.getStatus(),
                "submit 后账号应为 PENDING_APPROVAL");
        assertEquals(BorrowStatus.PENDING_APPROVAL, appAfterSubmit.getStatus(),
                "submit 后申请应为 PENDING_APPROVAL");
        assertEquals(CUSTODIAN_ID, appAfterSubmit.getCustodianId(),
                "custodianId 应正确落库");

        // ── F2: approve 后 account=IN_USE + borrowedBy=applicantId + application=BORROWED ──
        BorrowApplicationDTO approved = borrowService.approveApplication(
                submitted.getId(), "审批通过", custodian, false);

        flushAndClear();

        PlatformAccount afterApprove = accountRepository.findById(account.getId()).orElseThrow();
        AccountBorrowApplication appAfterApprove = applicationRepository.findById(submitted.getId()).orElseThrow();
        assertEquals(AccountStatus.IN_USE, afterApprove.getStatus(),
                "approve 后账号应为 IN_USE");
        assertEquals(APPLICANT_ID, afterApprove.getBorrowedBy(),
                "borrowedBy 应为申请人 ID");
        assertNotNull(afterApprove.getBorrowedAt(), "borrowedAt 应被填充");
        assertNotNull(afterApprove.getDueAt(), "dueAt 应被填充");
        assertEquals(BorrowStatus.BORROWED, appAfterApprove.getStatus(),
                "approve 后申请应为 BORROWED");
        assertNotNull(appAfterApprove.getApprovedAt(), "approvedAt 应被填充");
        assertEquals("审批通过", appAfterApprove.getApprovalComment());

        Integer returnCountBeforeReturn = afterApprove.getReturnCount();

        // ── F4: return 后 account=AVAILABLE + password 更新 + returnCount+1 + application=RETURNED ──
        BorrowApplicationDTO returned = borrowService.returnAccount(
                submitted.getId(), "newPassword123", LocalDateTime.now(), custodian, false);

        flushAndClear();

        PlatformAccount afterReturn = accountRepository.findById(account.getId()).orElseThrow();
        AccountBorrowApplication appAfterReturn = applicationRepository.findById(submitted.getId()).orElseThrow();
        assertEquals(AccountStatus.AVAILABLE, afterReturn.getStatus(),
                "return 后账号应为 AVAILABLE");
        assertNull(afterReturn.getBorrowedBy(), "borrowedBy 应被清空");
        assertEquals("newPassword123", afterReturn.getPassword(),
                "密码应被更新（NoOp 加密透传）");
        assertEquals(returnCountBeforeReturn + 1, afterReturn.getReturnCount(),
                "returnCount 应自增");
        assertEquals(BorrowStatus.RETURNED, appAfterReturn.getStatus(),
                "return 后申请应为 RETURNED");
        assertNotNull(appAfterReturn.getReturnedAt(), "returnedAt 应被填充");
    }

    // ════════════════════════════════════════════════════════════════════
    //  B. 权限豁免（CO-403）
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-403: 权限豁免")
    class PrivilegedRoleExemption {

        @Test
        @DisplayName("B1: isPrivileged=true 审批他人申请 → 成功，跨表一致")
        void privilegedRole_approveOthersApplication_succeeds() {
            PlatformAccount account = createAvailableAccount("b1");
            BorrowApplicationDTO submitted = borrowService.submitApplication(
                    buildRequest(account.getId(), CUSTODIAN_ID), buildUser(APPLICANT_ID));

            // OTHER_USER_ID 审批（非 custodian），但 isPrivileged=true
            BorrowApplicationDTO result = borrowService.approveApplication(
                    submitted.getId(), "管理员审批", buildUser(OTHER_USER_ID), true);

            flushAndClear();

            // BorrowApplicationDTO.status 是 String（API 契约），用 .name() 比较
            assertEquals(BorrowStatus.BORROWED.name(), result.getStatus());

            PlatformAccount updated = accountRepository.findById(account.getId()).orElseThrow();
            assertEquals(AccountStatus.IN_USE, updated.getStatus(),
                    "管理员审批后账号应为 IN_USE");
            assertEquals(APPLICANT_ID, updated.getBorrowedBy());
        }

        @Test
        @DisplayName("B2: isPrivileged=false 且非 custodian 审批 → 抛异常且状态不变")
        void nonPrivileged_nonCustodian_approve_throwsAndUnchanged() {
            PlatformAccount account = createAvailableAccount("b2");
            BorrowApplicationDTO submitted = borrowService.submitApplication(
                    buildRequest(account.getId(), CUSTODIAN_ID), buildUser(APPLICANT_ID));

            assertThrows(BusinessException.class,
                    () -> borrowService.approveApplication(
                            submitted.getId(), "应失败", buildUser(OTHER_USER_ID), false));

            flushAndClear();

            AccountBorrowApplication unchanged = applicationRepository.findById(submitted.getId()).orElseThrow();
            assertEquals(BorrowStatus.PENDING_APPROVAL, unchanged.getStatus(),
                    "申请状态应保持 PENDING_APPROVAL");

            PlatformAccount unchangedAccount = accountRepository.findById(account.getId()).orElseThrow();
            assertEquals(AccountStatus.PENDING_APPROVAL, unchangedAccount.getStatus(),
                    "账号状态应保持 PENDING_APPROVAL");
        }

        @Test
        @DisplayName("B5: findPendingApprovals 返回所有 PENDING_APPROVAL 申请")
        void findPendingApprovals_returnsAllPending() {
            PlatformAccount acct1 = createAvailableAccount("b5-1");
            PlatformAccount acct2 = createAvailableAccount("b5-2");
            borrowService.submitApplication(buildRequest(acct1.getId(), CUSTODIAN_ID), buildUser(APPLICANT_ID));
            borrowService.submitApplication(buildRequest(acct2.getId(), CUSTODIAN_ID), buildUser(OTHER_USER_ID));

            flushAndClear();

            List<BorrowApplicationDTO> pending = borrowService.findPendingApprovals();
            assertTrue(pending.size() >= 2,
                    "应返回至少 2 条待审批申请，实际: " + pending.size());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  C. 状态机边界
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("状态机边界")
    class StateMachineBoundaries {

        @Test
        @DisplayName("C1: approve 一个 BORROWED 状态申请 → 抛异常")
        void approveBorrowedApplication_throws() {
            PlatformAccount account = createAvailableAccount("c1");
            BorrowApplicationDTO submitted = borrowService.submitApplication(
                    buildRequest(account.getId(), CUSTODIAN_ID), buildUser(APPLICANT_ID));
            borrowService.approveApplication(submitted.getId(), "approved", buildUser(CUSTODIAN_ID), false);

            flushAndClear();

            assertThrows(BusinessException.class,
                    () -> borrowService.approveApplication(
                            submitted.getId(), "again", buildUser(CUSTODIAN_ID), false));
        }

        @Test
        @DisplayName("C4: return 一个 PENDING_APPROVAL 状态申请 → 抛异常")
        void returnPendingApplication_throws() {
            PlatformAccount account = createAvailableAccount("c4");
            BorrowApplicationDTO submitted = borrowService.submitApplication(
                    buildRequest(account.getId(), CUSTODIAN_ID), buildUser(APPLICANT_ID));

            flushAndClear();

            assertThrows(BusinessException.class,
                    () -> borrowService.returnAccount(
                            submitted.getId(), "newPwd123", LocalDateTime.now(), buildUser(CUSTODIAN_ID), false));
        }
    }
}
