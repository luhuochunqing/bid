package com.xiyu.bid.contractborrow.infrastructure.persistence.repository;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ContractBorrowApplicationJpaRepository
        extends JpaRepository<ContractBorrowApplicationEntity, Long>,
        JpaSpecificationExecutor<ContractBorrowApplicationEntity> {

    List<ContractBorrowApplicationEntity> findByOrderBySubmittedAtDesc();

    long countByStatus(ContractBorrowStatus status);

    long countByStatusInAndExpectedReturnDateBefore(
            Collection<ContractBorrowStatus> statuses,
            LocalDate expectedReturnDate
    );
}
