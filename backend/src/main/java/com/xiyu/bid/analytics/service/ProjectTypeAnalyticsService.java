package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.ProjectTypeAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProjectTypeAnalyticsService {

    private final ProjectTypeAnalyticsAssemblerService assemblerService;

    public ProjectTypeAnalyticsResponse getProjectTypes(LocalDate startDate, LocalDate endDate) {
        return assemblerService.getProjectTypes(startDate, endDate);
    }
}