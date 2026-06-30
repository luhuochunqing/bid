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

    /** CO-403: 修复管理员归还账号后申请表状态不同步问题。 */
    List<AccountBorrowApplication> findByAccountIdAndStatus(Long accountId, BorrowStatus status);

    /** CO-403: 我的审批 Tab —— 管理员查看全部申请，按创建时间倒序。 */
    List<AccountBorrowApplication> findAllByOrderByCreatedAtDesc();

    /** CO-403: 我的审批 Tab —— 绑定联系人查看自己负责的全部申请，按创建时间倒序。 */
    List<AccountBorrowApplication> findByCustodianIdOrderByCreatedAtDesc(Long custodianId);
}
