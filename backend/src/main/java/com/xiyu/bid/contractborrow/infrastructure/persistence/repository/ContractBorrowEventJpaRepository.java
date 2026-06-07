package com.xiyu.bid.contractborrow.infrastructure.persistence.repository;

import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractBorrowEventJpaRepository extends JpaRepository<ContractBorrowEventEntity, Long> {

    List<ContractBorrowEventEntity> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
