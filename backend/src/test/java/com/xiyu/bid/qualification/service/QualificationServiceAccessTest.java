package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.application.service.BorrowQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.CreateQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.DeleteQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.GetQualificationBorrowRecordsAppService;
import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.businessqualification.application.service.ReturnQualificationAppService;
import com.xiyu.bid.businessqualification.application.service.ScanExpiringQualificationsAppService;
import com.xiyu.bid.businessqualification.application.service.UpdateQualificationAppService;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.qualification.dto.QualificationBorrowRequest;
import com.xiyu.bid.qualification.dto.QualificationReturnRequest;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualificationServiceAccessTest {

    @Mock private CreateQualificationAppService createQualificationAppService;
    @Mock private UpdateQualificationAppService updateQualificationAppService;
    @Mock private BorrowQualificationAppService borrowQualificationAppService;
    @Mock private ReturnQualificationAppService returnQualificationAppService;
    @Mock private ListQualificationsAppService listQualificationsAppService;
    @Mock private GetQualificationBorrowRecordsAppService getQualificationBorrowRecordsAppService;
    @Mock private ScanExpiringQualificationsAppService scanExpiringQualificationsAppService;
    @Mock private DeleteQualificationAppService deleteQualificationAppService;
    @Mock private ProjectAccessScopeService projectAccessScopeService;

    @Test
    void borrowQualification_ShouldRejectNonNumericProjectId() {
        QualificationService service = newService();

        assertThatThrownBy(() -> service.borrowQualification(1L, borrowRequest("ABC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("项目 ID 必须为数字");

        verify(borrowQualificationAppService, never()).borrow(eq(1L), any());
    }

    @Test
    void borrowQualification_ShouldRejectInvisibleProjectBeforeBorrowing() {
        QualificationService service = newService();
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(99L);

        assertThatThrownBy(() -> service.borrowQualification(1L, borrowRequest("99")))
                .isInstanceOf(AccessDeniedException.class);

        verify(borrowQualificationAppService, never()).borrow(eq(1L), any());
    }

    @Test
    void getBorrowRecords_ShouldFilterAllRecordsByVisibleProjectsForNonAdmin() {
        QualificationService service = newService();
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(listQualificationsAppService.list(any())).thenReturn(List.of(
                qualification(1L),
                qualification(2L),
                qualification(3L)
        ));
        when(getQualificationBorrowRecordsAppService.getBorrowRecords()).thenReturn(List.of(
                loan(1L, 1L, "10", LoanStatus.BORROWED),
                loan(2L, 2L, null, LoanStatus.BORROWED),
                loan(3L, 3L, "99", LoanStatus.BORROWED)
        ));

        assertThat(service.getBorrowRecords(null))
                .extracting("projectId")
                .containsExactly("10", null);
    }

    @Test
    void getBorrowRecords_ShouldFilterQualificationRecordsByVisibleProjectsForNonAdmin() {
        QualificationService service = newService();
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(10L));
        when(listQualificationsAppService.get(1L)).thenReturn(qualification(1L));
        when(getQualificationBorrowRecordsAppService.getBorrowRecords(1L)).thenReturn(List.of(
                loan(1L, 1L, "10", LoanStatus.BORROWED),
                loan(2L, 1L, null, LoanStatus.BORROWED),
                loan(3L, 1L, "99", LoanStatus.BORROWED)
        ));

        assertThat(service.getBorrowRecords(1L))
                .extracting("projectId")
                .containsExactly("10", null);
    }

    @Test
    void returnQualification_ShouldRejectInvisibleActiveRecordBeforeReturning() {
        QualificationService service = newService();
        when(getQualificationBorrowRecordsAppService.getBorrowRecords(1L))
                .thenReturn(List.of(loan(7L, 1L, "99", LoanStatus.BORROWED)));
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(99L);

        assertThatThrownBy(() -> service.returnQualification(1L, QualificationReturnRequest.builder().build()))
                .isInstanceOf(AccessDeniedException.class);

        verify(returnQualificationAppService, never()).returnLoan(eq(1L), any());
    }

    @Test
    void returnQualificationByRecordId_ShouldRejectInvisibleRecordBeforeReturning() {
        QualificationService service = newService();
        when(getQualificationBorrowRecordsAppService.getBorrowRecords())
                .thenReturn(List.of(loan(7L, 1L, "99", LoanStatus.BORROWED)));
        doThrow(new AccessDeniedException("权限不足"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(99L);

        assertThatThrownBy(() -> service.returnQualificationByRecordId(7L, QualificationReturnRequest.builder().build()))
                .isInstanceOf(AccessDeniedException.class);

        verify(returnQualificationAppService, never()).returnLoanByRecordId(eq(7L), any());
    }

    private QualificationService newService() {
        return new QualificationService(
                createQualificationAppService,
                updateQualificationAppService,
                borrowQualificationAppService,
                returnQualificationAppService,
                listQualificationsAppService,
                getQualificationBorrowRecordsAppService,
                scanExpiringQualificationsAppService,
                deleteQualificationAppService,
                new QualificationDtoMapper(),
                projectAccessScopeService
        );
    }

    private QualificationBorrowRequest borrowRequest(String projectId) {
        return QualificationBorrowRequest.builder()
                .borrower("小王")
                .projectId(projectId)
                .build();
    }

    private QualificationLoan loan(Long id, Long qualificationId, String projectId, LoanStatus status) {
        return new QualificationLoan(
                id,
                qualificationId,
                "小王",
                "商务部",
                projectId,
                "项目资审",
                null,
                LocalDateTime.now().minusDays(id),
                LocalDate.now().plusDays(3),
                null,
                null,
                status
        );
    }

    private BusinessQualification qualification(Long id) {
        return BusinessQualification.create(
                id,
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
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
