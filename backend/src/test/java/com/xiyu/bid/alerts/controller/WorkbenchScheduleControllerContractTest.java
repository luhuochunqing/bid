package com.xiyu.bid.alerts.controller;

import com.xiyu.bid.calendar.dto.CalendarEventDTO;
import com.xiyu.bid.calendar.dto.ScheduleOverviewDTO;
import com.xiyu.bid.calendar.entity.EventType;
import com.xiyu.bid.workbench.controller.WorkbenchScheduleController;
import com.xiyu.bid.workbench.service.WorkbenchScheduleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkbenchScheduleControllerContractTest {

    @Mock
    private WorkbenchScheduleQueryService workbenchScheduleQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WorkbenchScheduleController(workbenchScheduleQueryService)
        ).build();
    }

    @Test
    void getScheduleOverview_ShouldReturnStructuredOverview() throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 30);
        ScheduleOverviewDTO response = ScheduleOverviewDTO.builder()
                .start(start)
                .end(end)
                .assigneeId(7L)
                .total(1)
                .urgent(1)
                .events(List.of(CalendarEventDTO.builder()
                        .id(1L)
                        .eventDate(LocalDate.of(2026, 4, 12))
                        .eventType(EventType.DEADLINE)
                        .title("项目截标")
                        .projectId(99L)
                        .isUrgent(true)
                        .build()))
                .build();

        when(workbenchScheduleQueryService.getScheduleOverview(eq(start), eq(end), eq(7L))).thenReturn(response);

        mockMvc.perform(get("/api/workbench/schedule-overview")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-30")
                        .param("assigneeId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assigneeId").value(7))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.events[0].title").value("项目截标"));

        verify(workbenchScheduleQueryService).getScheduleOverview(start, end, 7L);
    }
}
