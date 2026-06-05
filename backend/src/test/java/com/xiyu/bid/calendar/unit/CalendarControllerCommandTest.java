package com.xiyu.bid.calendar.unit;

import com.xiyu.bid.calendar.dto.CalendarEventCreateRequest;
import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.CalendarEventUpdateRequest;
import com.xiyu.bid.calendar.entity.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CalendarController command tests")
class CalendarControllerCommandTest extends AbstractCalendarControllerTest {

    @Test
    @DisplayName("POST /api/calendar - 应该成功创建事件")
    void shouldCreateEventSuccessfully() throws Exception {
        when(calendarService.createEvent(any(CalendarEventCreateRequest.class))).thenReturn(testEventDTO);

        mockMvc.perform(post("/api/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("项目截止日期"))
                .andExpect(jsonPath("$.data.eventType").value("DEADLINE"));

        verify(calendarService, times(1)).createEvent(any(CalendarEventCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/calendar - 应该拒绝创建空标题的事件")
    void shouldRejectEventWithEmptyTitle() throws Exception {
        createRequest.setTitle("");

        mockMvc.perform(post("/api/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(calendarService, never()).createEvent(any());
    }

    @Test
    @DisplayName("POST /api/calendar - 应该拒绝创建空日期的事件")
    void shouldRejectEventWithNullDate() throws Exception {
        createRequest.setEventDate(null);

        mockMvc.perform(post("/api/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(calendarService, never()).createEvent(any());
    }

    @Test
    @DisplayName("POST /api/calendar - 应该拒绝创建空类型的事件")
    void shouldRejectEventWithNullType() throws Exception {
        createRequest.setEventType(null);

        mockMvc.perform(post("/api/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

        verify(calendarService, never()).createEvent(any());
    }

    @Test
    @DisplayName("PUT /api/calendar/1 - 应该成功更新事件")
    void shouldUpdateEventSuccessfully() throws Exception {
        CalendarEventDTO updatedDTO = CalendarEventDTO.builder()
                .id(1L)
                .eventDate(LocalDate.of(2024, 3, 15))
                .eventType(EventType.DEADLINE)
                .title("更新后的标题")
                .isUrgent(false)
                .build();
        when(calendarService.updateEvent(eq(1L), any(CalendarEventUpdateRequest.class)))
                .thenReturn(updatedDTO);

        mockMvc.perform(put("/api/calendar/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("更新后的标题"))
                .andExpect(jsonPath("$.data.isUrgent").value(false));

        verify(calendarService, times(1)).updateEvent(eq(1L), any(CalendarEventUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/calendar/999 - 应该返回错误当更新不存在的事件时")
    void shouldReturnErrorWhenUpdatingNonExistentEvent() throws Exception {
        when(calendarService.updateEvent(eq(999L), any(CalendarEventUpdateRequest.class)))
                .thenThrow(new RuntimeException("CalendarEvent not found"));

        mockMvc.perform(put("/api/calendar/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        verify(calendarService, times(1)).updateEvent(eq(999L), any(CalendarEventUpdateRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/calendar/1 - 应该成功删除事件")
    void shouldDeleteEventSuccessfully() throws Exception {
        doNothing().when(calendarService).deleteEvent(1L);

        mockMvc.perform(delete("/api/calendar/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(calendarService, times(1)).deleteEvent(1L);
    }

    @Test
    @DisplayName("DELETE /api/calendar/999 - 应该返回错误当删除不存在的事件时")
    void shouldReturnErrorWhenDeletingNonExistentEvent() throws Exception {
        doThrow(new RuntimeException("CalendarEvent not found"))
                .when(calendarService).deleteEvent(999L);

        mockMvc.perform(delete("/api/calendar/999"))
                .andExpect(status().isNotFound());

        verify(calendarService, times(1)).deleteEvent(999L);
    }

    @Test
    @DisplayName("应该正确处理特殊字符")
    void shouldHandleSpecialCharacters() throws Exception {
        createRequest.setTitle("项目截止日期 <script>alert('xss')</script>");
        when(calendarService.createEvent(any(CalendarEventCreateRequest.class))).thenReturn(testEventDTO);

        mockMvc.perform(post("/api/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        verify(calendarService, times(1)).createEvent(any(CalendarEventCreateRequest.class));
    }

    @Test
    @DisplayName("应该正确处理边界日期值")
    void shouldHandleBoundaryDateValues() throws Exception {
        createRequest.setEventDate(LocalDate.of(2024, 1, 1));
        when(calendarService.createEvent(any(CalendarEventCreateRequest.class))).thenReturn(testEventDTO);

        mockMvc.perform(post("/api/calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        verify(calendarService, times(1)).createEvent(any(CalendarEventCreateRequest.class));
    }
}
