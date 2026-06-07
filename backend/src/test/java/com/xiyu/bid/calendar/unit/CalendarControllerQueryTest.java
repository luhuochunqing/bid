package com.xiyu.bid.calendar.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CalendarController query tests")
class CalendarControllerQueryTest extends AbstractCalendarControllerTest {

    @Test
    @DisplayName("GET /api/calendar - 应该成功获取日期范围内的事件")
    void shouldGetEventsByDateRangeSuccessfully() throws Exception {
        LocalDate start = LocalDate.of(2024, 3, 1);
        LocalDate end = LocalDate.of(2024, 3, 31);

        mockMvc.perform(get("/api/calendar")
                        .param("start", start.toString())
                        .param("end", end.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/calendar/month/2024/3 - 应该成功获取指定月份的事件")
    void shouldGetEventsByMonthSuccessfully() throws Exception {
        when(calendarService.getEventsByMonth(2024, 3)).thenReturn(List.of(testEventDTO));

        mockMvc.perform(get("/api/calendar/month/2024/3")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("项目截止日期"));
    }

    @Test
    @DisplayName("GET /api/calendar/month/2024/13 - 应该返回错误对于无效月份")
    void shouldReturnErrorForInvalidMonth() throws Exception {
        when(calendarService.getEventsByMonth(2024, 13))
                .thenThrow(new IllegalArgumentException("Invalid month: 13"));

        mockMvc.perform(get("/api/calendar/month/2024/13")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/calendar/project/100 - 应该成功获取项目事件")
    void shouldGetEventsByProjectSuccessfully() throws Exception {
        when(calendarService.getEventsByProject(100L)).thenReturn(List.of(testEventDTO));

        mockMvc.perform(get("/api/calendar/project/100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].projectId").value(100));
    }

    @Test
    @DisplayName("GET /api/calendar/urgent - 应该成功获取紧急事件")
    void shouldGetUrgentEventsSuccessfully() throws Exception {
        when(calendarService.getUrgentEvents()).thenReturn(List.of(testEventDTO));

        mockMvc.perform(get("/api/calendar/urgent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].isUrgent").value(true));
    }
}
