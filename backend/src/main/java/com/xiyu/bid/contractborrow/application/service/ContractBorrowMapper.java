package com.xiyu.bid.contractborrow.application.service;

import com.xiyu.bid.contractborrow.application.view.ContractBorrowEventView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowView;
import com.xiyu.bid.contractborrow.domain.model.ContractBorrowApplication;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowEventEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ContractBorrowMapper {

    public ContractBorrowApplication toDomain(ContractBorrowApplicationEntity entity) {
        return new ContractBorrowApplication(
            entity.getId(),
            entity.getContractNo(),
            entity.getContractName(),
            entity.getSourceName(),
            entity.getBorrowerName(),
            entity.getBorrowerDept(),
            entity.getCustomerName(),
            entity.getPurpose(),
            entity.getBorrowType(),
            entity.getExpectedReturnDate(),
            entity.getSubmittedAt(),
            entity.getApproverName(),
            entity.getApprovedAt(),
            entity.getRejectionReason(),
            entity.getRejectedAt(),
            entity.getReturnRemark(),
            entity.getReturnedAt(),
            entity.getCancelReason(),
            entity.getCancelledAt(),
            entity.getLastComment(),
            entity.getStatus()
        );
    }

    public void copyDomainToEntity(ContractBorrowApplication source, ContractBorrowApplicationEntity target) {
        target.setContractNo(source.contractNo());
        target.setContractName(source.contractName());
        target.setSourceName(source.sourceName());
        target.setBorrowerName(source.borrowerName());
        target.setBorrowerDept(source.borrowerDept());
        target.setCustomerName(source.customerName());
        target.setPurpose(source.purpose());
        target.setBorrowType(source.borrowType());
        target.setExpectedReturnDate(source.expectedReturnDate());
        target.setSubmittedAt(source.submittedAt());
        target.setApproverName(source.approverName());
        target.setApprovedAt(source.approvedAt());
        target.setRejectionReason(source.rejectionReason());
        target.setRejectedAt(source.rejectedAt());
        target.setReturnRemark(source.returnRemark());
        target.setReturnedAt(source.returnedAt());
        target.setCancelReason(source.cancelReason());
        target.setCancelledAt(source.cancelledAt());
        target.setLastComment(source.lastComment());
        target.setStatus(source.status());
    }

    public ContractBorrowView toView(ContractBorrowApplicationEntity entity, LocalDate today) {
        ContractBorrowApplication application = toDomain(entity);
        return new ContractBorrowView(
            application.id(),
            application.contractNo(),
            application.contractName(),
            application.sourceName(),
            application.borrowerName(),
            application.borrowerDept(),
            application.customerName(),
            application.purpose(),
            application.borrowType(),
            application.expectedReturnDate(),
            application.submittedAt(),
            application.approverName(),
            application.approvedAt(),
            application.rejectionReason(),
            application.rejectedAt(),
            application.returnRemark(),
            application.returnedAt(),
            application.cancelReason(),
            application.cancelledAt(),
            application.lastComment(),
            application.status(),
            application.displayStatus(today),
            application.isOverdue(today)
        );
    }

    public ContractBorrowEventView toEventView(ContractBorrowEventEntity entity) {
        return new ContractBorrowEventView(
            entity.getId(),
            entity.getApplicationId(),
            entity.getEventType(),
            entity.getStatusAfter(),
            entity.getActorName(),
            entity.getComment(),
            entity.getCreatedAt()
        );
    }
}
