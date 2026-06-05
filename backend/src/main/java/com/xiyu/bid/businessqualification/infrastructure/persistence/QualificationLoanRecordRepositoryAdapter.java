package com.xiyu.bid.businessqualification.infrastructure.persistence;

import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.port.QualificationLoanRecordRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.QualificationLoanRecordEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.QualificationLoanRecordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QualificationLoanRecordRepositoryAdapter implements QualificationLoanRecordRepository {

    private final QualificationLoanRecordJpaRepository jpaRepository;

    @Override
    public QualificationLoan save(QualificationLoan loan) {
        return toDomain(jpaRepository.save(toEntity(loan)));
    }

    @Override
    public Optional<QualificationLoan> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<QualificationLoan> findActiveByQualificationId(Long qualificationId) {
        return jpaRepository.findFirstByQualificationIdAndReturnedAtIsNullOrderByBorrowedAtDesc(qualificationId)
                .map(this::toDomain);
    }

    @Override
    public List<QualificationLoan> findByQualificationId(Long qualificationId) {
        return jpaRepository.findByQualificationIdOrderByBorrowedAtDesc(qualificationId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<QualificationLoan> findAllOrderByBorrowedAtDesc() {
        return jpaRepository.findAllByOrderByBorrowedAtDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    private QualificationLoanRecordEntity toEntity(QualificationLoan loan) {
        return QualificationLoanRecordEntity.builder()
                .id(loan.getId())
                .qualificationId(loan.getQualificationId())
                .borrower(loan.getBorrower())
                .department(loan.getDepartment())
                .projectId(loan.getProjectId())
                .purpose(loan.getPurpose())
                .remark(loan.getRemark())
                .borrowedAt(loan.getBorrowedAt())
                .expectedReturnDate(loan.getExpectedReturnDate())
                .returnedAt(loan.getReturnedAt())
                .returnRemark(loan.getReturnRemark())
                .status(loan.getStatus())
                .build();
    }

    private QualificationLoan toDomain(QualificationLoanRecordEntity entity) {
        return new QualificationLoan(
                entity.getId(),
                entity.getQualificationId(),
                entity.getBorrower(),
                entity.getDepartment(),
                entity.getProjectId(),
                entity.getPurpose(),
                entity.getRemark(),
                entity.getBorrowedAt(),
                entity.getExpectedReturnDate(),
                entity.getReturnedAt(),
                entity.getReturnRemark(),
                entity.getStatus()
        );
    }
}
