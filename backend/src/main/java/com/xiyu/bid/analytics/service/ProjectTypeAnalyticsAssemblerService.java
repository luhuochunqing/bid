package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.ProjectTypeAnalyticsResponse;
import com.xiyu.bid.analytics.dto.ProjectTypeDimensionDTO;
import com.xiyu.bid.analytics.model.ProjectTypeAggregate;
import com.xiyu.bid.analytics.model.ProjectTypeProjectRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectTypeAnalyticsAssemblerService {

    private final ProjectTypeAnalyticsQueryService queryService;
    private final ProjectTypeAnalyticsComputationService computationService;

    public ProjectTypeAnalyticsResponse getProjectTypes(LocalDate startDate, LocalDate endDate) {
        List<ProjectTypeProjectRow> rows = queryService.fetchProjectRows(startDate, endDate);
        List<ProjectTypeAggregate> aggregates = computationService.summarize(rows);

        long uncategorizedCount = aggregates.stream()
                .filter(aggregate -> ProjectTypeAnalyticsComputationService.UNCATEGORIZED_PROJECT_TYPE
                        .equals(aggregate.projectType()))
                .mapToLong(ProjectTypeAggregate::projectCount)
                .sum();
        BigDecimal totalAmount = aggregates.stream()
                .map(ProjectTypeAggregate::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ProjectTypeAnalyticsResponse.builder()
                .totalProjectCount((long) rows.size())
                .classifiedProjectCount(rows.size() - uncategorizedCount)
                .uncategorizedProjectCount(uncategorizedCount)
                .totalAmount(totalAmount)
                .dimensions(aggregates.stream().map(this::toDimensionDTO).toList())
                .build();
    }

    private ProjectTypeDimensionDTO toDimensionDTO(ProjectTypeAggregate aggregate) {
        return ProjectTypeDimensionDTO.builder()
                .projectType(aggregate.projectType())
                .projectCount(aggregate.projectCount())
                .activeProjectCount(aggregate.activeProjectCount())
                .wonCount(aggregate.wonCount())
                .totalAmount(aggregate.totalAmount())
                .percentage(aggregate.percentage())
                .winRate(aggregate.winRate())
                .build();
    }
}