package com.xiyu.bid.calendar.unit;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CalendarService query tests")
class CalendarServiceQueryTest extends AbstractCalendarServiceTest {

    @Test
    @DisplayName("应该成功获取指定月份的事件")
    void shouldGetEventsByMonthSuccessfully() {
        List<CalendarEvent> events = Arrays.asList(
                testEvent,
                CalendarEvent.builder()
                        .id(2L)
                        .eventDate(LocalDate.of(2024, 3, 20))
                        .eventType(EventType.MEETING)
                        .title("项目会议")
                        .projectId(100L)
                        .build()
        );
        when(repository.findByEventDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(events);

        List<CalendarEventDTO> result = calendarService.getEventsByMonth(2024, 3);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("项目截止日期");
        assertThat(result.get(1).getTitle()).isEqualTo("项目会议");
        verify(repository, times(1)).findByEventDateBetween(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("应该成功获取空列表当月份没有事件时")
    void shouldReturnEmptyListWhenMonthHasNoEvents() {
        when(repository.findByEventDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        List<CalendarEventDTO> result = calendarService.getEventsByMonth(2024, 12);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该成功根据项目ID获取事件")
    void shouldGetEventsByProjectSuccessfully() {
        List<CalendarEvent> events = Arrays.asList(
                testEvent,
                CalendarEvent.builder()
                        .id(2L)
                        .eventDate(LocalDate.of(2024, 3, 20))
                        .eventType(EventType.MEETING)
                        .title("项目会议")
                        .projectId(100L)
                        .build()
        );
        when(repository.findByProjectId(100L)).thenReturn(events);

        List<CalendarEventDTO> result = calendarService.getEventsByProject(100L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.getProjectId().equals(100L));
        verify(repository, times(1)).findByProjectId(100L);
    }

    @Test
    @DisplayName("应该成功返回空列表当项目没有事件时")
    void shouldReturnEmptyListWhenProjectHasNoEvents() {
        when(repository.findByProjectId(999L)).thenReturn(List.of());

        List<CalendarEventDTO> result = calendarService.getEventsByProject(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该成功获取所有紧急事件")
    void shouldGetUrgentEventsSuccessfully() {
        List<CalendarEvent> events = Arrays.asList(
                testEvent,
                CalendarEvent.builder()
                        .id(2L)
                        .eventDate(LocalDate.of(2024, 3, 20))
                        .eventType(EventType.MILESTONE)
                        .title("里程碑")
                        .isUrgent(true)
                        .build()
        );
        when(repository.findByIsUrgentTrue()).thenReturn(events);

        List<CalendarEventDTO> result = calendarService.getUrgentEvents();

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(CalendarEventDTO::getIsUrgent);
        verify(repository, times(1)).findByIsUrgentTrue();
    }

    @Test
    @DisplayName("应该成功返回空列表当没有紧急事件时")
    void shouldReturnEmptyListWhenNoUrgentEvents() {
        when(repository.findByIsUrgentTrue()).thenReturn(List.of());

        List<CalendarEventDTO> result = calendarService.getUrgentEvents();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该成功获取即将到来的事件")
    void shouldGetUpcomingEventsSuccessfully() {
        List<CalendarEvent> events = Arrays.asList(
                CalendarEvent.builder()
                        .id(1L)
                        .eventDate(LocalDate.now().plusDays(7))
                        .eventType(EventType.MEETING)
                        .title("项目会议")
                        .build(),
                CalendarEvent.builder()
                        .id(2L)
                        .eventDate(LocalDate.now().plusDays(14))
                        .eventType(EventType.MILESTONE)
                        .title("里程碑")
                        .build()
        );
        when(repository.findUpcomingEvents(any(LocalDate.class))).thenReturn(events);

        List<CalendarEventDTO> result = calendarService.getUpcomingEvents();

        assertThat(result).hasSize(2);
        verify(repository, times(1)).findUpcomingEvents(any(LocalDate.class));
    }

    @Test
    @DisplayName("应该成功返回空列表当没有即将到来的事件时")
    void shouldReturnEmptyListWhenNoUpcomingEvents() {
        when(repository.findUpcomingEvents(any(LocalDate.class))).thenReturn(List.of());

        List<CalendarEventDTO> result = calendarService.getUpcomingEvents();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应该正确处理无效的月份参数")
    void shouldHandleInvalidMonthParameters() {
        assertThatThrownBy(() -> calendarService.getEventsByMonth(2024, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid month");

        assertThatThrownBy(() -> calendarService.getEventsByMonth(2024, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid month");
    }

    @Test
    @DisplayName("应该正确处理负数年份")
    void shouldHandleNegativeYear() {
        assertThatThrownBy(() -> calendarService.getEventsByMonth(-2024, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid year");
    }
}
