package com.xiyu.bid.platform.repository;

import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-534: 验证 AccountBorrowApplicationRepository 的派生查询方法真实生成
 * ORDER BY created_at DESC SQL。
 *
 * 现有 PlatformAccountBorrowServiceTest 是 Mockito mock 单元测试，只验证"调用了
 * findAllByOrderByCreatedAtDesc"，无法发现 Spring Data JPA 派生方法名解析问题。
 * 本测试用 H2 + @DataJpaTest 真实执行查询，验证返回顺序。
 *
 * 手动设置不同 createdAt（绕过 @PrePersist 的 LocalDateTime.now()），
 * 确保排序可断言（毫秒内创建的申请 createdAt 可能相同导致顺序不稳定）。
 */
@DataJpaTest
@ActiveProfiles("test")
class AccountBorrowApplicationRepositoryOrderTest {

    @Autowired
    private AccountBorrowApplicationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc: 返回全部申请按 createdAt 降序")
    void findAllByOrderByCreatedAtDesc_returnsAllOrderByCreatedAtDesc() {
        // 手动设置不同 createdAt，绕过 @PrePersist
        AccountBorrowApplication oldest = saveWithCreatedAt(LocalDateTime.of(2026, 7, 7, 10, 0, 0));
        AccountBorrowApplication middle = saveWithCreatedAt(LocalDateTime.of(2026, 7, 7, 11, 0, 0));
        AccountBorrowApplication newest = saveWithCreatedAt(LocalDateTime.of(2026, 7, 7, 12, 0, 0));

        entityManager.flush();
        entityManager.clear();

        List<AccountBorrowApplication> result = repository.findAllByOrderByCreatedAtDesc();

        assertThat(result).extracting(AccountBorrowApplication::getId)
                .containsSubsequence(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    @DisplayName("findByCustodianIdOrderByCreatedAtDesc: 仅返回该 custodian 的申请并按 createdAt 降序")
    void findByCustodianIdOrderByCreatedAtDesc_returnsOwnOrderByCreatedAtDesc() {
        Long custodianId = 1001L;
        // custodianId 关联 3 条；其他 custodian 1 条作为干扰
        AccountBorrowApplication oldest = saveWithCreatedAtAndCustodian(LocalDateTime.of(2026, 7, 7, 10, 0, 0), custodianId);
        AccountBorrowApplication middle = saveWithCreatedAtAndCustodian(LocalDateTime.of(2026, 7, 7, 11, 0, 0), custodianId);
        AccountBorrowApplication newest = saveWithCreatedAtAndCustodian(LocalDateTime.of(2026, 7, 7, 12, 0, 0), custodianId);
        saveWithCreatedAtAndCustodian(LocalDateTime.of(2026, 7, 7, 13, 0, 0), 9999L);  // 干扰项

        entityManager.flush();
        entityManager.clear();

        List<AccountBorrowApplication> result = repository.findByCustodianIdOrderByCreatedAtDesc(custodianId);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(AccountBorrowApplication::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    private AccountBorrowApplication saveWithCreatedAt(LocalDateTime createdAt) {
        return saveWithCreatedAtAndCustodian(createdAt, 1001L);
    }

    private AccountBorrowApplication saveWithCreatedAtAndCustodian(LocalDateTime createdAt, Long custodianId) {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .accountId(2000L)
                .applicantId(3000L)
                .custodianId(custodianId)
                .purpose("CO-534 测试")
                .status(BorrowStatus.PENDING_APPROVAL)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        return repository.save(app);
    }
}
