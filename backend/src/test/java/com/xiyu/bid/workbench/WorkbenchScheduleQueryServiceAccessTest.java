package com.xiyu.bid.workbench;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.calendar.service.CalendarService;
import com.xiyu.bid.workbench.service.WorkbenchScheduleQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchScheduleQueryServiceAccessTest {

    @Mock
    private CalendarService calendarService;

    @InjectMocks
    private WorkbenchScheduleQueryService service;

    @Test
    void shouldDelegateToCalendarServiceForScheduleEvents() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        CalendarEventDTO visible = event(1L, 100L, LocalDate.of(2026, 5, 2));
        CalendarEventDTO urgent = event(2L, null, LocalDate.of(2026, 5, 1));
        when(calendarService.getEventsByDateRange(start, end)).thenReturn(List.of(visible, urgent));

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

        var response = service.getScheduleOverview(start, end, null);

        assertThat(response.getEvents()).extracting(CalendarEventDTO::getId).containsExactly(1L, 2L);
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
}
