package com.xiyu.bid.platform.repository;

import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Platform Account entities
 */
@Repository
public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {

    /**
     * Find account by username
     */
    Optional<PlatformAccount> findByUsername(String username);

    /**
     * Find all accounts by status
     */
    List<PlatformAccount> findByStatus(AccountStatus status);

    /**
     * Find accounts borrowed by a specific user
     */
    List<PlatformAccount> findByBorrowedBy(Long borrowedBy);

    /**
     * Count accounts by status
     */
    long countByStatus(AccountStatus status);

    /**
     * Find available accounts by platform type
     */
    List<PlatformAccount> findByPlatformTypeAndStatus(
        com.xiyu.bid.platform.entity.PlatformAccount.PlatformType platformType,
        AccountStatus status
    );

    /**
     * Find overdue borrowed accounts
     */
    @Query("SELECT a FROM PlatformAccount a WHERE a.status = :status AND a.dueAt < :now")
    List<PlatformAccount> findOverdueAccounts(@Param("status") AccountStatus status, @Param("now") java.time.LocalDateTime now);

    /**
     * Find accounts that need to be returned
     */
    @Query("SELECT a FROM PlatformAccount a WHERE a.borrowedBy = :userId AND a.dueAt < :now")
    List<PlatformAccount> findAccountsDueForReturn(@Param("userId") Long userId, @Param("now") java.time.LocalDateTime now);
}
