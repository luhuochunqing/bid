package com.xiyu.bid.platform.repository;

import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for AccountBorrowApplication entities.
 */
@Repository
public interface AccountBorrowApplicationRepository
        extends JpaRepository<AccountBorrowApplication, Long> {

    List<AccountBorrowApplication> findByApplicantId(Long applicantId);

    List<AccountBorrowApplication> findByAccountId(Long accountId);

    List<AccountBorrowApplication> findByCustodianId(Long custodianId);

    List<AccountBorrowApplication> findByStatus(BorrowStatus status);
}
