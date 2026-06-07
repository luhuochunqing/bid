package com.xiyu.bid.casework.application;

import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationLoanRecordEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationLoanRecordJpaRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 资质借阅检查服务，将 Controller 与 Repository/Entity 解耦。
 */
@Service
@RequiredArgsConstructor
public class QualificationBorrowCheckService {

    private final ProjectAccessScopeService projectAccessScopeService;
    private final QualificationLoanRecordJpaRepository qualificationLoanRecordJpaRepository;

    public record BorrowCheckResult(boolean allowed, String reason, long borrowRecordId) {}

    public BorrowCheckResult checkBorrow(Long qualificationId, Long projectId) {
        boolean isAdmin = projectAccessScopeService.currentUserHasAdminAccess();

        if (isAdmin) {
            return new BorrowCheckResult(true, "管理员免审批借阅", 0L);
        }

        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIdsForCurrentUser();
        if (!allowedProjectIds.contains(projectId)) {
            return new BorrowCheckResult(false, "未绑定已审批通过的借阅流程，请先提交借阅审批", 0L);
        }

        Optional<QualificationLoanRecordEntity> loanRecordOpt = qualificationLoanRecordJpaRepository
                .findFirstByQualificationIdAndReturnedAtIsNullOrderByBorrowedAtDesc(qualificationId);

        if (loanRecordOpt.isPresent()) {
            QualificationLoanRecordEntity record = loanRecordOpt.get();
            if (LoanStatus.BORROWED.equals(record.getStatus()) && String.valueOf(projectId).equals(record.getProjectId())) {
                return new BorrowCheckResult(true, "已关联通过审批的资质借阅流程", record.getId());
            }
        }

        return new BorrowCheckResult(false, "未绑定已审批通过的借阅流程，请先提交借阅审批", 0L);
    }
}
