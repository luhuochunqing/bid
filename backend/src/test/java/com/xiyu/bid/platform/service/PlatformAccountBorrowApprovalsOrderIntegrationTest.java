// Input: PlatformAccountBorrowService, repositories, PlatformAccount fixtures
// Output: 端到端验证 findAllApprovals 按 createdAt 倒序返回
// Pos: Test/集成测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.dto.BorrowApplicationDTO;
import com.xiyu.bid.platform.dto.BorrowApplicationRequest;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-534: 我的审批列表按申请创建时间倒序展示 — Service 层端到端测试。
 *
 * <p>背景：CO-403 已在 {@link PlatformAccountBorrowService#findAllApprovals(Long)} 修复
 * 排序（调用 {@code findAllByOrderByCreatedAtDesc} /
 * {@code findByCustodianIdOrderByCreatedAtDesc}）。但现有
 * {@code PlatformAccountBorrowServiceTest} 是 Mockito mock 单元测试，只验证"调用了
 * 正确方法名"，无法发现以下场景：
 * <ul>
 *   <li>Service 层误改为调用无排序的方法（如 {@code findAll()}）</li>
 *   <li>Mapper 转换时丢失顺序</li>
 *   <li>Spring Data JPA 派生方法名解析问题</li>
 * </ul>
 * 本测试用 H2 + @SpringBootTest 端到端验证 Service 真实调用并按序返回。</p>
 *
 * <p><b>局限性说明：</b>
 * <ul>
 *   <li><b>H2 vs MySQL ENUM 差异</b>：被测实体使用 {@code @Enumerated(EnumType.STRING)}，
 *       在 H2 上识别为 VARCHAR，不会校验 MySQL ENUM 约束。ENUM 相关验证应走
 *       {@link PlatformAccountBorrowServiceMysqlIntegrationTest}（MySQL 集成测试）。
 *       若未来 entity 加新 ENUM 值，本测试无法发现 MySQL 端约束问题。</li>
 *   <li><b>schema 漂移回避</b>：{@link PlatformAccountBorrowServiceMysqlIntegrationTest}
 *       本是首选（真实 MySQL round-trip），但因 {@code warehouse_attachment.type} 列
 *       schema validation 失败（found varchar, expecting enum，与 CO-534 无关），
 *       改用 H2 端到端测试。该 schema 漂移应单独 issue 修复。</li>
 *   <li><b>分页未覆盖</b>：CO-534 测试要点提到"翻页后顺序仍正确"，但 Repository 方法
 *       返回 {@code List} 而非 {@code Page}，分页在 Controller/前端层。本测试不覆盖
 *       分页，需 Controller 层集成测试覆盖。</li>
 * </ul></p>
 *
 * <p><b>时间戳设计</b>：通过手动 sleep 间隔确保 createdAt 唯一，避免毫秒内创建的
 * 申请 createdAt 相同导致排序不可断言。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class PlatformAccountBorrowApprovalsOrderIntegrationTest {

    @Autowired
    private PlatformAccountBorrowService borrowService;

    @Autowired
    private PlatformAccountRepository accountRepository;

    private static final Long CUSTODIAN_ID = 7001L;
    private static final Long APPLICANT_ID = 7002L;
    private static final Long OTHER_CUSTODIAN_ID = 7003L;

    @TestConfiguration
    static class TestBeans {
        @Bean(name = "passwordEncryptionUtil")
        @Primary
        PasswordEncryptionUtil passwordEncryptionUtil() {
            return new PasswordEncryptionUtil() {
                @Override
                public void initialize() {
                }

                @Override
                public String encrypt(String plainPassword) {
                    return plainPassword;
                }

                @Override
                public String decrypt(String encryptedPassword) {
                    return encryptedPassword;
                }
            };
        }
    }

    @Test
    @DisplayName("D1: 管理员路径 — findAllApprovals(null) 返回全部申请按 createdAt 降序")
    void findAllApprovals_admin_returnsAllOrderByCreatedAtDesc() throws InterruptedException {
        // 注意：同一账号 submit 后会变 PENDING_APPROVAL，不可重复 submit；
        // 为隔离状态，每条申请用独立账号。
        // 通过 sleep 间隔确保 createdAt 唯一（@PrePersist 用 LocalDateTime.now()），
        // 避免毫秒内创建的申请 createdAt 相同导致排序不可断言。
        PlatformAccount account1 = createAvailableAccount("d1-1");
        BorrowApplicationDTO first = submit(account1, CUSTODIAN_ID);
        Thread.sleep(10);

        PlatformAccount account2 = createAvailableAccount("d1-2");
        BorrowApplicationDTO second = submit(account2, CUSTODIAN_ID);
        Thread.sleep(10);

        PlatformAccount account3 = createAvailableAccount("d1-3");
        BorrowApplicationDTO third = submit(account3, CUSTODIAN_ID);

        List<BorrowApplicationDTO> result = borrowService.findAllApprovals(null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(BorrowApplicationDTO::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    @DisplayName("D2: 绑定联系人路径 — findAllApprovals(custodianId) 仅返回该 custodian 的申请并按 createdAt 降序")
    void findAllApprovals_custodian_returnsOwnOrderByCreatedAtDesc() throws InterruptedException {
        // CUSTODIAN_ID 关联 3 条申请（来自不同 applicant / account，但 custodian 都是 CUSTODIAN_ID）；
        // OTHER_CUSTODIAN_ID 关联 1 条作为干扰项，验证过滤正确。
        PlatformAccount account1 = createAvailableAccount("d2-1");
        BorrowApplicationDTO first = submit(account1, CUSTODIAN_ID);
        Thread.sleep(10);

        PlatformAccount account2 = createAvailableAccount("d2-2");
        BorrowApplicationDTO second = submit(account2, CUSTODIAN_ID);
        Thread.sleep(10);

        PlatformAccount account3 = createAvailableAccount("d2-3");
        BorrowApplicationDTO third = submit(account3, CUSTODIAN_ID);
        Thread.sleep(10);

        // 干扰项：不同 custodian 的申请
        PlatformAccount otherAccount = createAvailableAccountWithContact("d2-other", OTHER_CUSTODIAN_ID);
        submit(otherAccount, OTHER_CUSTODIAN_ID);

        List<BorrowApplicationDTO> result = borrowService.findAllApprovals(CUSTODIAN_ID);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(BorrowApplicationDTO::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    // ── 辅助方法 ──

    private PlatformAccount createAvailableAccount(String suffix) {
        return createAvailableAccountWithContact(suffix, CUSTODIAN_ID);
    }

    private PlatformAccount createAvailableAccountWithContact(String suffix, Long contactPersonId) {
        return accountRepository.saveAndFlush(PlatformAccount.builder()
                .username("co534-acct-" + suffix)
                .password("encrypted-pwd")
                .accountName("CO-534 测试账号-" + suffix)
                .contactPerson(contactPersonId)
                .status(AccountStatus.AVAILABLE)
                .build());
    }

    private BorrowApplicationDTO submit(PlatformAccount account, Long custodianId) {
        BorrowApplicationRequest request = BorrowApplicationRequest.builder()
                .accountId(account.getId())
                .custodianId(custodianId)
                .purpose("CO-534 排序测试")
                .build();
        return borrowService.submitApplication(request, buildUser(APPLICANT_ID));
    }

    private User buildUser(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }
}
