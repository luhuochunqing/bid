package com.xiyu.bid.calendar.unit;

import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.entity.CalendarEvent;
import com.xiyu.bid.calendar.entity.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CalendarService command tests")
class CalendarServiceCommandTest extends AbstractCalendarServiceTest {

    @Test
    @DisplayName("应该成功创建日历事件")
    void shouldCreateEventSuccessfully() {
        when(repository.save(any(CalendarEvent.class))).thenReturn(testEvent);

        CalendarEventDTO result = calendarService.createEvent(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("项目截止日期");
        verify(repository, times(1)).save(any(CalendarEvent.class));
    }

    @Test
    @DisplayName("应该拒绝创建空标题的事件")
    void shouldRejectEventWithEmptyTitle() {
        createRequest.setTitle("");

        assertThatThrownBy(() -> calendarService.createEvent(createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title is required");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("应该拒绝创建空日期的事件")
    void shouldRejectEventWithNullDate() {
        createRequest.setEventDate(null);

        assertThatThrownBy(() -> calendarService.createEvent(createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event date is required");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("应该拒绝创建空类型的事件")
    void shouldRejectEventWithNullType() {
        createRequest.setEventType(null);

        assertThatThrownBy(() -> calendarService.createEvent(createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event type is required");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("应该成功更新日历事件")
    void shouldUpdateEventSuccessfully() {
        when(repository.findById(1L)).thenReturn(Optional.of(testEvent));
        when(repository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalendarEventDTO result = calendarService.updateEvent(1L, updateRequest);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("更新后的标题");
        assertThat(result.getIsUrgent()).isFalse();
        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).save(any(CalendarEvent.class));
    }

    @Test
    @DisplayName("应该抛出异常当更新不存在的事件时")
    void shouldThrowExceptionWhenUpdatingNonExistentEvent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarService.updateEvent(999L, updateRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CalendarEvent not found");
        verify(repository, times(1)).findById(999L);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("应该成功删除日历事件")
    void shouldDeleteEventSuccessfully() {
        when(repository.findById(1L)).thenReturn(Optional.of(testEvent));
        doNothing().when(repository).deleteById(1L);

        calendarService.deleteEvent(1L);

        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("应该抛出异常当删除不存在的事件时")
    void shouldThrowExceptionWhenDeletingNonExistentEvent() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarService.deleteEvent(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CalendarEvent not found");
        verify(repository, times(1)).findById(999L);
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("应该正确处理空字符串输入")
    void shouldHandleEmptyStringInput() {
        updateRequest.setTitle("");
        when(repository.findById(1L)).thenReturn(Optional.of(testEvent));

        assertThatThrownBy(() -> calendarService.updateEvent(1L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Title cannot be empty");
    }

    @Test
    @DisplayName("应该正确处理边界值")
    void shouldHandleBoundaryValues() {
        CalendarEventCreateRequest boundaryRequest = CalendarEventCreateRequest.builder()
                .eventDate(LocalDate.of(2024, 1, 1))
                .eventType(EventType.DEADLINE)
                .title("边界测试")
                .build();
        CalendarEvent boundaryEvent = CalendarEvent.builder()
                .id(1L)
                .eventDate(LocalDate.of(2024, 1, 1))
                .eventType(EventType.DEADLINE)
                .title("边界测试")
                .build();
        when(repository.save(any(CalendarEvent.class))).thenReturn(boundaryEvent);

        CalendarEventDTO result = calendarService.createEvent(boundaryRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEventDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }
}
