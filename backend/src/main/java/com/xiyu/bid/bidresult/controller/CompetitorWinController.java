package com.xiyu.bid.bidresult.controller;

import com.xiyu.bid.bidresult.dto.CompetitorWinDTO;
import com.xiyu.bid.bidresult.dto.CompetitorWinRequest;
import com.xiyu.bid.bidresult.service.CompetitorWinCommandService;
import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import com.xiyu.bid.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bid-results/competitor-wins")
@RequiredArgsConstructor
public class CompetitorWinController {

    private static final String ADMIN_MANAGER_STAFF_EXPR = "hasAnyRole('ADMIN', 'MANAGER', 'STAFF')";

    private final CompetitorWinCommandService competitorWinCommandService;
    private final BidResultCurrentUserResolver currentUserResolver;

    @PostMapping
    @PreAuthorize(ADMIN_MANAGER_STAFF_EXPR)
    public ResponseEntity<ApiResponse<CompetitorWinDTO>> create(
            @RequestBody CompetitorWinRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = currentUserResolver.resolve(userDetails);
        return ResponseEntity.ok(ApiResponse.success(competitorWinCommandService.register(request, user.getId(), user.getFullName())));
    }
}
