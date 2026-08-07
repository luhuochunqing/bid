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

    @Test
    void shouldTruncateTenderIdsWhenExceedingInClauseLimit() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));

        // 构造 600 个 tenderId，触发 MAX_TENDER_IDS_FOR_IN_CLAUSE=500 截断
        List<Long> overLimitTenderIds = new java.util.ArrayList<>();
        for (long i = 1L; i <= 600L; i++) {
            overLimitTenderIds.add(i);
        }
        when(projectRepository.findTenderIdsByProjectIds(anyList())).thenReturn(overLimitTenderIds);

        Tender openingTender = tender(1L, "截断后仍可见", 200L, LocalDateTime.of(2026, 7, 10, 9, 30), null);
        when(tenderRepository.findTendersByBidOpeningTimeAndTenderIds(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(openingTender));
        when(tenderRepository.findTendersByRegistrationDeadlineAndTenderIds(anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        // 验证传给 Repository 的 tenderIds 被截断为 500
        org.mockito.ArgumentCaptor<java.util.List<Long>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(tenderRepository).findTendersByBidOpeningTimeAndTenderIds(captor.capture(), any(), any());
        assertThat(captor.getValue()).hasSize(500);
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getTitle()).isEqualTo("截断后仍可见");
    }

    // ==================== 防御性去重（buildDedupKey）测试 ====================

    /**
     * 去重核心：Tender 派生事件与手动事件合并后，完全重复的事件（同类型+同日期+同标题）必须合并为一条。
     * 覆盖生产事故：日历显示重复项目。
     */
    @Test
    void duplicateEventsWithSameTypeDateTitleMustBeDeduplicated() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        // 两个相同手动事件（同类型 MEETING + 同日期 + 同标题）
        CalendarEventDTO ev1 = CalendarEventDTO.builder()
                .id(1L).eventDate(LocalDate.of(2026, 7, 10))
                .eventType(EventType.MEETING).title("重复会议").projectId(100L).isUrgent(false).build();
        CalendarEventDTO ev2 = CalendarEventDTO.builder()
                .id(2L).eventDate(LocalDate.of(2026, 7, 10))
                .eventType(EventType.MEETING).title("重复会议").projectId(100L).isUrgent(false).build();
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of(ev1, ev2));
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getId()).isEqualTo(1L);
        assertThat(response.getTotal()).isEqualTo(1);
    }

    /**
     * 去重边界：同标题同日期但类型不同（开标 vs 会议）→ 不是重复，必须全部保留。
     * 防止去重键过宽导致不同类型事件被误合并。
     */
    @Test
    void sameTitleDateButDifferentTypeMustNotBeDeduplicated() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        Tender openingTender = tender(10L, "标讯-X", 200L, LocalDateTime.of(2026, 7, 10, 9, 30), null);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of(openingTender));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        // 只有一个 OPENING 事件；无重复
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getEventType()).isEqualTo(EventType.OPENING);
    }

    /**
     * 去重边界：两个不同 Tender（同标题同开标日期）经 buildTenderDerivedEvents 派生出相同事件 → 合并为一条。
     * 覆盖 Tender 表重复记录导致的日历重复。
     */
    @Test
    void duplicateTendersMustDeduplicateDerivedOpeningEvents() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        LocalDateTime opening = LocalDateTime.of(2026, 7, 10, 9, 30);
        Tender dup1 = tender(10L, "重复开标标讯", 200L, opening, null);
        Tender dup2 = tender(11L, "重复开标标讯", 200L, opening, null);
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of(dup1, dup2));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        // 去重后仅保留 1 条 OPENING 事件
        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getEventType()).isEqualTo(EventType.OPENING);
        assertThat(response.getEvents().get(0).getId()).isEqualTo(10L);
        assertThat(response.getTotal()).isEqualTo(1);
    }

    /**
     * 去重 null 安全：Tender title 为 null 时派生事件去重不得抛 NPE。
     */
    @Test
    void nullTitleTenderDerivedEventMustNotThrowDuringDedup() {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of());
        when(projectAccessScopeService.currentUserHasGlobalAccess()).thenReturn(true);
        Tender nullTitle = tender(10L, null, 200L, LocalDateTime.of(2026, 7, 10, 9, 30), null);
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of(nullTitle));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of());

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).hasSize(1);
        assertThat(response.getEvents().get(0).getTitle()).isNull();
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
