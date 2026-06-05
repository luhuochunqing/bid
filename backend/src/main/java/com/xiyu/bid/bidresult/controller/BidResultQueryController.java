package com.xiyu.bid.bidresult.controller;

import com.xiyu.bid.bidresult.dto.BidResultCompetitorReportRowDTO;
import com.xiyu.bid.bidresult.dto.BidResultDetailDTO;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultDTO;
import com.xiyu.bid.bidresult.dto.BidResultOverviewDTO;
import com.xiyu.bid.bidresult.dto.BidResultReminderDTO;
import com.xiyu.bid.bidresult.service.BidResultQueryService;
import com.xiyu.bid.bidresult.service.CompetitorReportQueryService;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bid-results")
@RequiredArgsConstructor
public class BidResultQueryController {

    private static final String ADMIN_MANAGER_STAFF_EXPR = "hasAnyRole('ADMIN', 'MANAGER', 'STAFF')";

    private final BidResultQueryService queryService;
    private final CompetitorReportQueryService competitorReportQueryService;

    @GetMapping("/overview")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultOverviewDTO> getOverview() {
        return ApiResponse.success(queryService.getOverview());
    }

    @GetMapping("/fetch-results")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<List<BidResultFetchResultDTO>> getFetchResults() {
        return ApiResponse.success(queryService.getFetchResults());
    }

    @GetMapping("/reminders")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<List<BidResultReminderDTO>> getReminders() {
        return ApiResponse.success(queryService.getReminders());
    }

    @GetMapping("/competitor-report")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<List<BidResultCompetitorReportRowDTO>> getCompetitorReport() {
        return ApiResponse.success(competitorReportQueryService.getCompetitorReport());
    }

    @GetMapping("/{id}")
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ApiResponse<BidResultDetailDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(queryService.getDetail(id));
    }
}

