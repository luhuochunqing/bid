package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationReturnCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.port.QualificationLoanRecordRepository;
import com.xiyu.bid.businessqualification.domain.service.QualificationLoanPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnQualificationAppServiceTest {

    @Mock private BusinessQualificationRepository qualificationRepository;
    @Mock private QualificationLoanRecordRepository loanRecordRepository;

    @Test
    void returnLoanByRecordId_ShouldReturnTheTargetRecordInsteadOfActiveRecordLookupResult() {
        ReturnQualificationAppService appService = new ReturnQualificationAppService(
                qualificationRepository,
                loanRecordRepository,
                new QualificationLoanPolicy()
        );
        QualificationLoan targetRecord = loan(2L, "200");
        when(loanRecordRepository.findById(2L)).thenReturn(Optional.of(targetRecord));
        when(qualificationRepository.findById(9L)).thenReturn(Optional.of(qualification()));
        when(loanRecordRepository.save(any(QualificationLoan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(qualificationRepository.save(any(BusinessQualification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QualificationLoan returned = appService.returnLoanByRecordId(
                2L,
                QualificationReturnCommand.builder().returnRemark("已归还").build()
        );

        ArgumentCaptor<QualificationLoan> savedLoan = ArgumentCaptor.forClass(QualificationLoan.class);
        verify(loanRecordRepository).save(savedLoan.capture());
        assertThat(returned.id()).isEqualTo(2L);
        assertThat(savedLoan.getValue().id()).isEqualTo(2L);
        assertThat(savedLoan.getValue().status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(savedLoan.getValue().returnRemark()).isEqualTo("已归还");
        verify(loanRecordRepository, never()).findActiveByQualificationId(9L);
    }

    @Test
    void returnLoanByRecordId_ShouldRejectTargetRecordThatIsNotBorrowed() {
        ReturnQualificationAppService appService = new ReturnQualificationAppService(
                qualificationRepository,
                loanRecordRepository,
                new QualificationLoanPolicy()
        );
        when(loanRecordRepository.findById(2L))
                .thenReturn(Optional.of(loan(2L, "200").markReturned(LocalDateTime.now().minusDays(1), "已归还")));
        when(qualificationRepository.findById(9L)).thenReturn(Optional.of(qualification()));

        assertThatThrownBy(() -> appService.returnLoanByRecordId(
                2L,
                QualificationReturnCommand.builder().returnRemark("再次归还").build()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该资质当前没有活动借阅记录");

        verify(loanRecordRepository, never()).save(any(QualificationLoan.class));
    }

    private BusinessQualification qualification() {
        return BusinessQualification.create(
                9L,
                "高新技术企业证书",
                QualificationSubject.of(QualificationSubjectType.COMPANY, "西域科技"),
                QualificationCategory.PRODUCT,
                "GX-001",
                "科技局",
                "张三",
                new ValidityPeriod(LocalDate.now().minusYears(1), LocalDate.now().plusYears(1)),
                new ReminderPolicy(true, 30, null),
                LoanStatus.BORROWED,
                "小王",
                null,
                "100",
                null,
                null,
                null,
                List.of()
        );
    }

    private QualificationLoan loan(Long id, String projectId) {
        return new QualificationLoan(
                id,
                9L,
                "小王",
                "商务部",
                projectId,
                "项目资审",
                null,
                LocalDateTime.now().minusDays(id),
                LocalDate.now().plusDays(3),
                null,
                null,
                LoanStatus.BORROWED
        );
    }
}
