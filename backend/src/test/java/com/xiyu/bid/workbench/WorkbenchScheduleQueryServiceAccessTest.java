package com.xiyu.bid.workbench;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.service.CalendarService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.workbench.service.WorkbenchScheduleQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchScheduleQueryServiceAccessTest {

    @Mock
    private CalendarService calendarService;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    @InjectMocks
    private WorkbenchScheduleQueryService service;

    @Test
    void shouldDelegateToCalendarServiceForScheduleEvents() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        CalendarEventDTO visible = event(1L, 100L, LocalDate.of(2026, 5, 2));
        CalendarEventDTO urgent = event(2L, null, LocalDate.of(2026, 5, 1));
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of(visible, urgent));
        // 非全局访问且无可见项目 → 不聚合 Tender 事件
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, 99L);

        assertThat(response.getEvents()).hasSize(2);
        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getUrgent()).isZero(); // urgent event has isUrgent=false
        verify(calendarService).getEventsByDateRange(start, end);
    }

    @Test
    void shouldSortEventsByDate() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        CalendarEventDTO later = event(2L, null, LocalDate.of(2026, 5, 15));
        CalendarEventDTO earlier = event(1L, null, LocalDate.of(2026, 5, 2));
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of(later, earlier));
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).extracting(CalendarEventDTO::getId).containsExactly(1L, 2L);
    }

    @Test
    void shouldAggregateTenderOpeningAndDeadlineEventsForGlobalAccess() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);

        Tender openingTender = tender(10L, "某标讯-开标", 200L, LocalDateTime.of(2026, 7, 10, 9, 30), null);
        Tender deadlineTender = tender(11L, "某标讯-报名截止", 201L, null, LocalDateTime.of(2026, 7, 5, 17, 0));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(openingTender));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(deadlineTender));

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).hasSize(2);
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getEventType)
                .containsExactlyInAnyOrder(EventType.OPENING, EventType.DEADLINE);
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getTitle)
                .containsExactlyInAnyOrder("某标讯-开标", "某标讯-报名截止");
        // 透传 Tender.id 和 projectId（review 修复）
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getId)
                .containsExactlyInAnyOrder(10L, 11L);
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getProjectId)
                .containsExactlyInAnyOrder(200L, 201L);
        // 事件按日期升序：7/5 在前，7/10 在后
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getEventDate)
                .containsExactly(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 10));
        verify(projectAccessScopeService, never()).getAllowedProjectIdsForCurrentUser();
    }

    @Test
    void shouldFilterTenderEventsByAllowedProjectIdsForNonGlobalAccess() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L, 101L));
        when(projectRepository.findTenderIdsByProjectIds(anyList())).thenReturn(List.of(10L, 11L));

        Tender openingTender = tender(10L, "可见标讯-开标", 200L, LocalDateTime.of(2026, 7, 10, 9, 30), null);
        Tender deadlineTender = tender(11L, "可见标讯-报名截止", 201L, null, LocalDateTime.of(2026, 7, 5, 17, 0));
        when(tenderRepository.findTendersByBidOpeningTimeAndTenderIds(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(openingTender));
        when(tenderRepository.findTendersByRegistrationDeadlineAndTenderIds(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(deadlineTender));

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).hasSize(2);
        verify(tenderRepository, never()).findTendersByBidOpeningTimeBetween(any(), any());
        verify(tenderRepository, never()).findTendersByRegistrationDeadlineBetween(any(), any());
    }

    @Test
    void shouldReturnEmptyTenderEventsWhenNonGlobalAccessHasNoAllowedProjects() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        CalendarEventDTO calendarEvent = event(1L, 100L, LocalDate.of(2026, 7, 15));
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of(calendarEvent));
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        // 仍返回 calendar_events 表里的事件，只是不聚合 Tender 事件
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getId()).isEqualTo(1L);
        verify(projectRepository, never()).findTenderIdsByProjectIds(anyList());
        verify(tenderRepository, never()).findTendersByBidOpeningTimeAndTenderIds(anyList(), any(), any());
    }

    @Test
    void shouldReturnEmptyTenderEventsWhenAllowedProjectsHaveNoTenders() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));
        when(projectRepository.findTenderIdsByProjectIds(anyList())).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).isEmpty();
        verify(tenderRepository, never()).findTendersByBidOpeningTimeAndTenderIds(anyList(), any(), any());
    }

    @Test
    void shouldMergeCalendarEventsWithTenderEvents() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        CalendarEventDTO meeting = event(1L, 100L, LocalDate.of(2026, 7, 20));
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of(meeting));
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        Tender openingTender = tender(10L, "开标事件", 200L, LocalDateTime.of(2026, 7, 10, 9, 30), null);
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of(openingTender));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).hasSize(2);
        // 按日期排序：7/10 开标在前，7/20 会议在后
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getEventDate)
                .containsExactly(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 20));
        assertThat(response.getEvents()).extracting(CalendarEventDTO::getTitle)
                .containsExactly("开标事件", "事件1");
    }

    private CalendarEventDTO event(Long id, Long projectId, LocalDate eventDate) {
        return CalendarEventDTO.builder()
                .id(id)
                .eventDate(eventDate)
                .eventType(EventType.MEETING)
                .title("事件" + id)
                .projectId(projectId)
                .isUrgent(false)
                .build();
    }

    private Tender tender(Long id, String title, Long projectId, LocalDateTime bidOpeningTime, LocalDateTime registrationDeadline) {
        return Tender.builder()
                .id(id)
                .title(title)
                .projectId(projectId)
                .bidOpeningTime(bidOpeningTime)
                .registrationDeadline(registrationDeadline)
                .build();
    }
}
