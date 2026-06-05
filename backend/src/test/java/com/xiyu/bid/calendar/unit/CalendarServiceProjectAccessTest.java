package com.xiyu.bid.calendar.unit;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CalendarService project access tests")
class CalendarServiceProjectAccessTest extends AbstractCalendarServiceTest {

    @Test
    @DisplayName("创建含不可见项目的事件应被拒绝")
    void shouldRejectCreateWhenProjectInvisible() {
        createRequest.setProjectId(200L);
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(200L);

        assertThatThrownBy(() -> calendarService.createEvent(createRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("更新到不可见项目应被拒绝")
    void shouldRejectUpdateWhenTargetProjectInvisible() {
        when(repository.findById(1L)).thenReturn(Optional.of(testEvent));
        doNothing().when(projectAccessScopeService).assertCurrentUserCanAccessProject(100L);
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(200L);

        CalendarEventUpdateRequest request = CalendarEventUpdateRequest.builder()
                .projectId(200L)
                .build();

        assertThatThrownBy(() -> calendarService.updateEvent(1L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("日期范围查询只返回无项目或可见项目事件")
    void shouldFilterDateRangeEventsByProjectVisibility() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));
        when(repository.findByEventDateBetween(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)))
                .thenReturn(List.of(event(1L, 100L), event(2L, 200L), event(3L, null)));

        List<CalendarEventDTO> result = calendarService.getEventsByDateRange(
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 31)
        );

        assertThat(result).extracting(CalendarEventDTO::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("月份查询只返回无项目或可见项目事件")
    void shouldFilterMonthEventsByProjectVisibility() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));
        when(repository.findByEventDateBetween(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)))
                .thenReturn(List.of(event(1L, 100L), event(2L, 200L), event(3L, null)));

        List<CalendarEventDTO> result = calendarService.getEventsByMonth(2024, 3);

        assertThat(result).extracting(CalendarEventDTO::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("紧急事件查询只返回无项目或可见项目事件")
    void shouldFilterUrgentEventsByProjectVisibility() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));
        when(repository.findByIsUrgentTrue()).thenReturn(List.of(event(1L, 100L), event(2L, 200L), event(3L, null)));

        List<CalendarEventDTO> result = calendarService.getUrgentEvents();

        assertThat(result).extracting(CalendarEventDTO::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("即将到来事件查询只返回无项目或可见项目事件")
    void shouldFilterUpcomingEventsByProjectVisibility() {
        when(projectAccessScopeService.currentUserHasAdminAccess()).thenReturn(false);
        when(projectAccessScopeService.getAllowedProjectIdsForCurrentUser()).thenReturn(List.of(100L));
        when(repository.findUpcomingEvents(any(LocalDate.class)))
                .thenReturn(List.of(event(1L, 100L), event(2L, 200L), event(3L, null)));

        List<CalendarEventDTO> result = calendarService.getUpcomingEvents();

        assertThat(result).extracting(CalendarEventDTO::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("按项目查询应先断言项目访问")
    void shouldAssertProjectAccessBeforeProjectQuery() {
        doThrow(new AccessDeniedException("权限不足，无法访问该项目"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(200L);

        assertThatThrownBy(() -> calendarService.getEventsByProject(200L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("权限不足");

        verify(repository, never()).findByProjectId(200L);
    }

    private CalendarEvent event(Long id, Long projectId) {
        return CalendarEvent.builder()
                .id(id)
                .eventDate(LocalDate.of(2024, 3, id.intValue()))
                .eventType(EventType.MEETING)
                .title("事件" + id)
                .projectId(projectId)
                .isUrgent(true)
                .build();
    }
}
