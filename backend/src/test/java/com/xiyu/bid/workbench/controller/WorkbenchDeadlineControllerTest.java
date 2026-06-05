package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import com.xiyu.bid.workbench.service.WorkbenchDeadlineQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkbenchDeadlineControllerTest {

    @Mock
    private WorkbenchDeadlineQueryService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkbenchDeadlineController(service)).build();
    }

    @Test
    void shouldReturnDeadlineStats() throws Exception {
        when(service.getDeadlineStats(any())).thenReturn(new WorkbenchDeadlineStatsDTO(
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(2, 5, 12),
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(1, 3, 8),
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(0, 1, 4)
        ));

        mvc.perform(get("/api/workbench/deadline-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationDeadline.todayCount").value(2))
                .andExpect(jsonPath("$.data.registrationDeadline.weekCount").value(5))
                .andExpect(jsonPath("$.data.bidOpening.todayCount").value(1))
                .andExpect(jsonPath("$.data.depositDeadline.monthCount").value(4));
    }

    @Test
    void shouldReturnZeroStatsWhenNoDeadlines() throws Exception {
        when(service.getDeadlineStats(any())).thenReturn(new WorkbenchDeadlineStatsDTO(
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(0, 0, 0),
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(0, 0, 0),
                new WorkbenchDeadlineStatsDTO.DeadlinePeriodStatsDTO(0, 0, 0)
        ));

        mvc.perform(get("/api/workbench/deadline-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationDeadline.todayCount").value(0));
    }
}
