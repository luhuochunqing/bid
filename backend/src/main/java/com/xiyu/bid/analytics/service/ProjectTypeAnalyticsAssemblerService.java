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

/**
 * M3 项目类型分析装配服务：编排查询与计算，输出响应 DTO。
 * <p>项目级数据权限由上游 {@link ProjectTypeAnalyticsQueryService} 经
 * {@code ProjectAccessScopeService} 统一过滤，本类只消费已授权范围内的行，
 * 不独立做项目访问决策。
 */
@Service
@RequiredArgsConstructor
public class ProjectTypeAnalyticsAssemblerService {

    private final ProjectTypeAnalyticsQueryService queryService;
    private final ProjectTypeAnalyticsComputationService computationService;

    public ProjectTypeAnalyticsResponse getProjectTypes(LocalDate startDate, LocalDate endDate) {
        List<ProjectTypeProjectRow> rows = queryService.fetchProjectRows(startDate, endDate);
        List<ProjectTypeAggregate> aggregates = computationService.summarize(rows);

        // P1-4 口径：只统计 5 个标准分类，未分类不计数，totalProjectCount = 已分类总数
        long classifiedProjectCount = aggregates.stream()
                .mapToLong(ProjectTypeAggregate::projectCount)
                .sum();
        BigDecimal totalAmount = aggregates.stream()
                .map(ProjectTypeAggregate::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ProjectTypeAnalyticsResponse.builder()
                .totalProjectCount(classifiedProjectCount)
                .classifiedProjectCount(classifiedProjectCount)
                // 废弃字段：新口径下未分类不计数，恒为 0（保留字段避免序列化破坏）
                .uncategorizedProjectCount(0L)
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