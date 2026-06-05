package com.xiyu.bid.analytics.controller;

import com.xiyu.bid.analytics.dto.CustomerTypeAnalyticsResponse;
import com.xiyu.bid.analytics.dto.CustomerTypeDrillDownRowDTO;
import com.xiyu.bid.analytics.service.CustomerTypeAnalyticsAssemblerService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class CustomerTypeAnalyticsController {

    private final CustomerTypeAnalyticsAssemblerService customerTypeAnalyticsService;

    @GetMapping("/customer-types")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CustomerTypeAnalyticsResponse>> getCustomerTypes(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerTypeAnalyticsService.getCustomerTypes(startDate, endDate)));
    }

    @GetMapping("/drilldown/customer-type")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CustomerTypeDrillDownRowDTO>>> getCustomerTypeDrillDown(
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                customerTypeAnalyticsService.getCustomerTypeDrillDown(customerType, startDate, endDate)
        ));
    }
}
