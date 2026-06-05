package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.calendar.dto.ScheduleOverviewDTO;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workbench.service.WorkbenchScheduleQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@Slf4j
public class WorkbenchScheduleController {

    private final WorkbenchScheduleQueryService workbenchScheduleQueryService;

    @GetMapping("/schedule-overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<ScheduleOverviewDTO>> getScheduleOverview(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end,
            @RequestParam(required = false) Long assigneeId) {
        log.info("GET /api/workbench/schedule-overview - Fetching schedule overview from {} to {}, assignee={}", start, end, assigneeId);
        return ResponseEntity.ok(ApiResponse.success(
                workbenchScheduleQueryService.getScheduleOverview(start, end, assigneeId)
        ));
    }
}
