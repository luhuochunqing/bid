package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.ProjectTypeAnalyticsResponse;
import com.xiyu.bid.analytics.service.ProjectTypeAnalyticsService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectTypeAnalyticsController {

    private final ProjectTypeAnalyticsService projectTypeAnalyticsService;

    @GetMapping("/project-types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectTypeAnalyticsResponse>> getProjectTypes(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                projectTypeAnalyticsService.getProjectTypes(startDate, endDate)
        ));
    }
}