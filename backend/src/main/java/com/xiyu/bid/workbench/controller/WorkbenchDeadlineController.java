package com.xiyu.bid.workbench.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.workbench.dto.WorkbenchDeadlineStatsDTO;
import com.xiyu.bid.workbench.service.WorkbenchDeadlineQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
@Slf4j
public class WorkbenchDeadlineController {

    private final WorkbenchDeadlineQueryService deadlineQueryService;

    @GetMapping("/deadline-stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkbenchDeadlineStatsDTO>> getDeadlineStats() {
        log.info("GET /api/workbench/deadline-stats");
        return ResponseEntity.ok(ApiResponse.success(
                deadlineQueryService.getDeadlineStats(LocalDate.now())
        ));
    }
}
