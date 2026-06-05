package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CustomerTypeAnalyticsResponse;
import com.xiyu.bid.analytics.dto.CustomerTypeDimensionDTO;
import com.xiyu.bid.analytics.dto.CustomerTypeDrillDownRowDTO;
import com.xiyu.bid.analytics.model.CustomerTypeAggregate;
import com.xiyu.bid.analytics.model.CustomerTypeProjectRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerTypeAnalyticsAssemblerService {

    private final CustomerTypeAnalyticsQueryService queryService;
    private final CustomerTypeAnalyticsComputationService computationService;

    public CustomerTypeAnalyticsResponse getCustomerTypes(LocalDate startDate, LocalDate endDate) {
        List<CustomerTypeProjectRow> rows = queryService.fetchProjectRows(startDate, endDate);
        List<CustomerTypeAggregate> aggregates = computationService.summarize(rows);

        long uncategorizedCount = aggregates.stream()
                .filter(aggregate -> CustomerTypeAnalyticsComputationService.UNCATEGORIZED_CUSTOMER_TYPE
                        .equals(aggregate.customerType()))
                .mapToLong(CustomerTypeAggregate::projectCount)
                .sum();
        BigDecimal totalAmount = aggregates.stream()
                .map(CustomerTypeAggregate::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CustomerTypeAnalyticsResponse.builder()
                .totalProjectCount((long) rows.size())
                .classifiedProjectCount(rows.size() - uncategorizedCount)
                .uncategorizedProjectCount(uncategorizedCount)
                .totalAmount(totalAmount)
                .dimensions(aggregates.stream().map(this::toDimensionDTO).toList())
                .build();
    }

    public List<CustomerTypeDrillDownRowDTO> getCustomerTypeDrillDown(
            String customerType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return computationService.filterByCustomerType(
                        queryService.fetchProjectRows(startDate, endDate),
                        customerType
                )
                .stream()
                .map(this::toDrillDownRowDTO)
                .toList();
    }

    private CustomerTypeDimensionDTO toDimensionDTO(
            CustomerTypeAggregate aggregate
    ) {
        return CustomerTypeDimensionDTO.builder()
                .customerType(aggregate.customerType())
                .projectCount(aggregate.projectCount())
                .activeProjectCount(aggregate.activeProjectCount())
                .wonCount(aggregate.wonCount())
                .totalAmount(aggregate.totalAmount())
                .percentage(aggregate.percentage())
                .winRate(aggregate.winRate())
                .build();
    }

    private CustomerTypeDrillDownRowDTO toDrillDownRowDTO(
            CustomerTypeProjectRow row
    ) {
        return CustomerTypeDrillDownRowDTO.builder()
                .projectId(row.projectId())
                .tenderId(row.tenderId())
                .projectName(row.projectName())
                .tenderTitle(row.tenderTitle())
                .customer(row.customer())
                .customerType(computationService.normalizeCustomerType(row.customerType()))
                .status(row.projectStatus() == null ? null : row.projectStatus().name())
                .outcome(computationService.deriveOutcome(row))
                .managerId(row.managerId())
                .managerName(resolveManagerName(row))
                .amount(row.amount() == null ? BigDecimal.ZERO : row.amount())
                .startDate(row.referenceDate())
                .endDate(row.endDate())
                .build();
    }

    private String resolveManagerName(CustomerTypeProjectRow row) {
        if (row.managerName() != null && !row.managerName().isBlank()) {
            return row.managerName();
        }
        if (row.managerId() == null) {
            return "-";
        }
        return "用户#" + row.managerId();
    }
}
