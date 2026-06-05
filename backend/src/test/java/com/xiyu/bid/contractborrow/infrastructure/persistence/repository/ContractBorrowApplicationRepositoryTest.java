package com.xiyu.bid.contractborrow.infrastructure.persistence.repository;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class ContractBorrowApplicationRepositoryTest {

    @Autowired
    private ContractBorrowApplicationJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveStaleApplication_ShouldFailWithOptimisticLockingConflict() {
        ContractBorrowApplicationEntity saved = repository.saveAndFlush(sampleEntity());

        ContractBorrowApplicationEntity firstCopy = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(firstCopy);

        ContractBorrowApplicationEntity secondCopy = repository.findById(saved.getId()).orElseThrow();

        secondCopy.setStatus(ContractBorrowStatus.APPROVED);
        repository.saveAndFlush(secondCopy);

        firstCopy.setStatus(ContractBorrowStatus.REJECTED);
        assertThatThrownBy(() -> repository.saveAndFlush(firstCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private ContractBorrowApplicationEntity sampleEntity() {
        return ContractBorrowApplicationEntity.builder()
                .contractNo("HT-2026-0421")
                .contractName("西域智算中心年度框架合同")
                .borrowerName("小王")
                .purpose("投标文件复核")
                .borrowType("原件借阅")
                .expectedReturnDate(LocalDate.of(2026, 4, 30))
                .submittedAt(LocalDateTime.of(2026, 4, 21, 10, 30))
                .status(ContractBorrowStatus.PENDING_APPROVAL)
                .build();
    }
}
