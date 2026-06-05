package com.xiyu.bid.workbench.service;

import com.xiyu.bid.fees.repository.FeeRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchDeadlineQueryServiceTest {

    @Mock TenderRepository tenderRepository;
    @Mock FeeRepository feeRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @InjectMocks WorkbenchDeadlineQueryService service;

    @Test
    void adminShouldSeeAllDeadlines() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any()))
                .thenReturn(List.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);

        assertThat(result.registrationDeadline().todayCount()).isEqualTo(1);
        assertThat(result.bidOpening().todayCount()).isZero();
        assertThat(result.depositDeadline().todayCount()).isZero();
        verifyNoInteractions(projectRepository);
    }

    @Test
    void managerShouldSeeOnlyOwnProjects() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L, 2L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L, 2L))).thenReturn(List.of(10L));
        when(tenderRepository.findRegistrationDeadlinesByTenderIds(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of(LocalDateTime.of(2026, 5, 17, 10, 0)));
        when(tenderRepository.findBidOpeningTimesByTenderIds(eq(List.of(10L)), any(), any()))
                .thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L, 2L)), any(), any()))
                .thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isEqualTo(1);
    }

    /**
     * P0-1 regression guard: non-admin user with completely empty project scope MUST get zero
     * counts and MUST NOT hit any repository (no data leak).
     */
    @Test
    void nonAdminWithEmptyProjectScopeMustGetZeroCountsWithoutDataAccess() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);

        assertThat(result.registrationDeadline().todayCount()).isZero();
        assertThat(result.registrationDeadline().weekCount()).isZero();
        assertThat(result.registrationDeadline().monthCount()).isZero();
        assertThat(result.bidOpening().todayCount()).isZero();
        assertThat(result.depositDeadline().todayCount()).isZero();
        // Critical: repositories must NOT be hit when the user has no project access
        verifyNoInteractions(tenderRepository, feeRepository, projectRepository);
    }

    @Test
    void managerWithEmptyTenderIdsShouldGetZeroCounts() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L));
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L))).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L)), any(), any()))
                .thenReturn(List.of());

        WorkbenchDeadlineStatsDTO result = service.getDeadlineStats(today);
        assertThat(result.registrationDeadline().todayCount()).isZero();
    }

    /**
     * P1 regression guard: when current week spans a month boundary, the query window
     * must include the out-of-month days so that weekly counts don't under-report.
     * Example: today = 2026-08-01 (Saturday) → week = Jul 27 ~ Aug 2.
     * Query start MUST be <= 2026-07-27 (week's Monday), not 2026-08-01 (month start).
     */
    @Test
    void crossMonthWeekWindowMustExpandQueryRangeBackward() {
        var today = LocalDate.of(2026, 8, 1); // Saturday
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        service.getDeadlineStats(today);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tenderRepository).findRegistrationDeadlinesBetween(startCap.capture(), endCap.capture());

        assertThat(startCap.getValue())
                .as("query start should cover the week's Monday (Jul 27) which is before monthStart (Aug 1)")
                .isEqualTo(LocalDateTime.of(2026, 7, 27, 0, 0));
        assertThat(endCap.getValue()).isEqualTo(LocalDate.of(2026, 8, 31).atTime(java.time.LocalTime.MAX));
    }

    /**
     * P1 regression guard: when current week extends past month end, query window
     * must include the out-of-month days going forward.
     * Example: today = 2026-05-30 (Saturday) → week = May 25 ~ May 31 (within month).
     * Example: today = 2026-06-30 (Tuesday) → week = Jun 29 ~ Jul 5, query end must
     * cover Jul 5, not just Jun 30.
     */
    @Test
    void crossMonthWeekWindowMustExpandQueryRangeForward() {
        var today = LocalDate.of(2026, 6, 30); // Tuesday
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(true);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());
        when(tenderRepository.findRegistrationDeadlinesBetween(any(), any())).thenReturn(List.of());
        when(tenderRepository.findBidOpeningTimesBetween(any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesBetween(any(), any())).thenReturn(List.of());

        service.getDeadlineStats(today);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tenderRepository).findRegistrationDeadlinesBetween(startCap.capture(), endCap.capture());

        assertThat(startCap.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(endCap.getValue())
                .as("query end should cover the week's Sunday (Jul 5) which is after monthEnd (Jun 30)")
                .isEqualTo(LocalDate.of(2026, 7, 5).atTime(java.time.LocalTime.MAX));
    }

    /**
     * FMEA guard: when allowedTenderIds exceeds MAX_TENDER_IDS_FOR_IN_CLAUSE (500),
     * the list must be truncated and the repository must receive a safe-sized sublist.
     */
    @Test
    void tenderIdsExceedingInClauseLimitMustBeTruncated() {
        var today = LocalDate.of(2026, 5, 17);
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(1L));

        List<Long> hugeTenderIds = java.util.stream.LongStream.rangeClosed(1, 600).boxed().toList();
        when(projectRepository.findTenderIdsByProjectIds(List.of(1L))).thenReturn(hugeTenderIds);
        when(tenderRepository.findRegistrationDeadlinesByTenderIds(any(), any(), any())).thenReturn(List.of());
        when(tenderRepository.findBidOpeningTimesByTenderIds(any(), any(), any())).thenReturn(List.of());
        when(feeRepository.findDepositDeadlinesByProjectIds(eq(List.of(1L)), any(), any())).thenReturn(List.of());

        service.getDeadlineStats(today);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tenderRepository).findRegistrationDeadlinesByTenderIds(captor.capture(), any(), any());
        verify(tenderRepository).findBidOpeningTimesByTenderIds(captor.capture(), any(), any());

        for (List<Long> captured : captor.getAllValues()) {
            assertThat(captured.size())
                    .as("tender IDs passed to repo must not exceed 500")
                    .isLessThanOrEqualTo(500);
            assertThat(captured.get(0)).isEqualTo(1L);
            assertThat(captured.get(captured.size() - 1)).isEqualTo(500L);
        }
    }
}
