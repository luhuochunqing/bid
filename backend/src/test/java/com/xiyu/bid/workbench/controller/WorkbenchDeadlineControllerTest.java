package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.workbench.domain.DeadlinePeriod;
import com.xiyu.bid.workbench.dto.DeadlineItemDTO;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineItemsDTO;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import com.xiyu.bid.workbench.service.WorkbenchDeadlineQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
        mvc = MockMvcBuilders.standaloneSetup(new WorkbenchDeadlineController(service))
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
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

    // ==================== CO-593: /deadline-items tests ====================

    @Test
    void shouldReturnDeadlineItemsWithDefaultWeekPeriod() throws Exception {
        when(service.getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.WEEK)))
                .thenReturn(new WorkbenchDeadlineItemsDTO(
                        List.of(new DeadlineItemDTO(10L, "标讯A", "2026-05-17", 10L, "tender")),
                        List.of(new DeadlineItemDTO(20L, "项目X", "2026-05-18", 100L, "project")),
                        List.of()
                ));

        mvc.perform(get("/api/workbench/deadline-items")) // 默认 period=week
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationDeadline[0].id").value(10))
                .andExpect(jsonPath("$.data.registrationDeadline[0].name").value("标讯A"))
                .andExpect(jsonPath("$.data.registrationDeadline[0].date").value("2026-05-17"))
                .andExpect(jsonPath("$.data.registrationDeadline[0].targetId").value(10))
                .andExpect(jsonPath("$.data.registrationDeadline[0].targetType").value("tender"))
                .andExpect(jsonPath("$.data.bidOpening[0].name").value("项目X"))
                .andExpect(jsonPath("$.data.bidOpening[0].targetType").value("project"))
                .andExpect(jsonPath("$.data.depositDeadline").isArray())
                .andExpect(jsonPath("$.data.depositDeadline").isEmpty());

        verify(service).getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.WEEK));
    }

    @Test
    void shouldParseTodayPeriodParam() throws Exception {
        when(service.getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.TODAY)))
                .thenReturn(WorkbenchDeadlineItemsDTO.empty());

        mvc.perform(get("/api/workbench/deadline-items").param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationDeadline").isArray())
                .andExpect(jsonPath("$.data.registrationDeadline").isEmpty());

        verify(service).getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.TODAY));
    }

    @Test
    void shouldParseMonthPeriodParam() throws Exception {
        when(service.getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.MONTH)))
                .thenReturn(WorkbenchDeadlineItemsDTO.empty());

        mvc.perform(get("/api/workbench/deadline-items").param("period", "month"))
                .andExpect(status().isOk());

        verify(service).getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.MONTH));
    }

    @Test
    void shouldFallbackToWeekWhenPeriodParamInvalid() throws Exception {
        when(service.getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.WEEK)))
                .thenReturn(WorkbenchDeadlineItemsDTO.empty());

        mvc.perform(get("/api/workbench/deadline-items").param("period", "garbage"))
                .andExpect(status().isOk());

        // DeadlinePeriod.fromStringOrDefault 对无效值降级为 WEEK
        verify(service).getDeadlineItems(any(LocalDate.class), eq(DeadlinePeriod.WEEK));
    }
}
