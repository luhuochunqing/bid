package com.xiyu.bid.workbench;

import com.xiyu.bid.workbench.controller.WorkbenchScheduleController;
import com.xiyu.bid.workbench.service.WorkbenchScheduleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkbenchScheduleController tests")
class WorkbenchScheduleControllerTest {

    @Mock
    private WorkbenchScheduleQueryService workbenchScheduleQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkbenchScheduleController(workbenchScheduleQueryService))
                .build();
    }

    @Test
    @DisplayName("GET /api/workbench/schedule-overview should filter and summarize event range")
    void shouldReturnScheduleOverview() throws Exception {
        when(workbenchScheduleQueryService.getScheduleOverview(
                java.time.LocalDate.of(2026, 4, 1),
                java.time.LocalDate.of(2026, 4, 20),
                9L
        )).thenReturn(com.xiyu.bid.calendar.dto.ScheduleOverviewDTO.builder()
                .start(java.time.LocalDate.of(2026, 4, 1))
                .end(java.time.LocalDate.of(2026, 4, 20))
                .assigneeId(9L)
                .total(2)
                .urgent(1)
                .events(java.util.List.of(
                        com.xiyu.bid.calendar.dto.CalendarEventDTO.builder()
                                .id(1L)
                                .title("A 项目截标")
                                .projectId(101L)
                                .build(),
                        com.xiyu.bid.calendar.dto.CalendarEventDTO.builder()
                                .id(2L)
                                .title("B 项目评审")
                                .projectId(102L)
                                .build()
                ))
                .build());

        mockMvc.perform(get("/api/workbench/schedule-overview")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-20")
                        .param("assigneeId", "9")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.start[0]").value(2026))
                .andExpect(jsonPath("$.data.start[1]").value(4))
                .andExpect(jsonPath("$.data.start[2]").value(1))
                .andExpect(jsonPath("$.data.end[0]").value(2026))
                .andExpect(jsonPath("$.data.end[1]").value(4))
                .andExpect(jsonPath("$.data.end[2]").value(20))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.urgent").value(1))
                .andExpect(jsonPath("$.data.events.length()").value(2))
                .andExpect(jsonPath("$.data.events[0].id").value(1))
                .andExpect(jsonPath("$.data.events[1].id").value(2));

        verify(workbenchScheduleQueryService).getScheduleOverview(
                java.time.LocalDate.of(2026, 4, 1),
                java.time.LocalDate.of(2026, 4, 20),
                9L
        );
    }
}
